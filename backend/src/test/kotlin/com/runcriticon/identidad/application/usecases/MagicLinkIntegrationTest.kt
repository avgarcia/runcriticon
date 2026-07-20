package com.runcriticon.identidad.application.usecases
import com.runcriticon.identidad.application.ports.inbound.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.inbound.MagicLinkEmailRequested
import com.runcriticon.identidad.application.ports.outbound.security.TokenHasher
import com.runcriticon.identidad.application.usecases.account.ActivateAccountCommand
import com.runcriticon.identidad.application.usecases.invitation.InviteCoachCommand
import com.runcriticon.identidad.application.usecases.magiclink.ConsumeMagicLinkCommand
import com.runcriticon.identidad.application.usecases.magiclink.RequestMagicLinkCommand
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.infrastructure.persistence.repositories.AuditEventEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.InvitationEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.MagicLinkEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.PasswordHistoryEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Integración del login con magic link (LAL-11, ADR-0003 D5) sobre Postgres real (Testcontainers).
 * Ejercita el recorrido completo: un usuario activo pide el enlace (entregado vía outbox al
 * [FakeEmailSender]), lo consume y crea sesión; reusarlo o usarlo caducado se rechaza; y un email
 * inexistente no emite nada (respuesta neutra). Reutiliza [FakeEmailConfig]/[FakeEmailSender] de
 * [CoachInvitationIntegrationTest] (mismo paquete).
 */
@SpringBootTest
@Testcontainers
@Import(FakeEmailConfig::class, UnlimitedRateLimitConfig::class)
class MagicLinkIntegrationTest {
    @Autowired private lateinit var inviteCoach: InviteCoachCommand

    @Autowired private lateinit var activateAccount: ActivateAccountCommand

    @Autowired private lateinit var requestMagicLink: RequestMagicLinkCommand

    @Autowired private lateinit var consumeMagicLink: ConsumeMagicLinkCommand

    @Autowired private lateinit var userEntityRepository: UserEntityRepository

    @Autowired private lateinit var invitationEntityRepository: InvitationEntityRepository

    @Autowired private lateinit var magicLinkEntityRepository: MagicLinkEntityRepository

    @Autowired private lateinit var passwordHistoryEntityRepository: PasswordHistoryEntityRepository

    @Autowired private lateinit var auditEventEntityRepository: AuditEventEntityRepository

    @Autowired private lateinit var tokenHasher: TokenHasher

    @Autowired private lateinit var emailSender: FakeEmailSender

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun configure(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("runcriticon.security.token-hmac-secret") { "test-token-hmac-secret-0123456789" }
            registry.add("runcriticon.observability.userid-hash-salt") { "test-userid-hash-salt-not-prod" }
        }
    }

    private val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)
    private val password = "clave-clave-clave"

    @BeforeEach
    fun limpiar() {
        magicLinkEntityRepository.deleteAll()
        invitationEntityRepository.deleteAll()
        passwordHistoryEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
        auditEventEntityRepository.deleteAll()
        emailSender.sent.clear()
        emailSender.magicLinksSent.clear()
    }

    @Test
    fun `un usuario activo pide magic link, entra, y no puede reusarlo (CA 1-2)`() {
        seedActiveCoach("ana@club.test")

        requestMagicLink.execute(clubId, "ana@club.test", "203.0.113.1").shouldBeRight()
        val rawToken = awaitMagicLinkFor("ana@club.test").rawToken.value

        val principal = consumeMagicLink.execute(rawToken).shouldBeRight()
        principal.clubId shouldBe clubId.value
        principal.role shouldBe Role.ENTRENADOR

        // Un solo uso: reusar el mismo token se rechaza.
        consumeMagicLink.execute(rawToken).shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()
    }

    @Test
    fun `un magic link caducado se rechaza (CA 3)`() {
        seedActiveCoach("ana@club.test")
        requestMagicLink.execute(clubId, "ana@club.test", "203.0.113.1").shouldBeRight()
        val rawToken = awaitMagicLinkFor("ana@club.test").rawToken.value

        // Forzar la caducidad en BD (>15 min).
        val stored =
            magicLinkEntityRepository.findByTokenHash(tokenHasher.hash(RawToken(rawToken)).value).shouldNotBeNull()
        stored.expiresAt = Instant.now().minus(Duration.ofMinutes(1))
        magicLinkEntityRepository.save(stored)

        consumeMagicLink.execute(rawToken).shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>()
    }

    @Test
    fun `un email inexistente no emite magic link (respuesta neutra, CA 1)`() {
        requestMagicLink.execute(clubId, "nadie@club.test", "203.0.113.1").shouldBeRight()

        magicLinkEntityRepository.count() shouldBe 0
        emailSender.magicLinksSent.none { it.to.value == "nadie@club.test" } shouldBe true
    }

    private fun seedActiveCoach(email: String) {
        inviteCoach.execute(admin, "Ana Coach", email).shouldBeRight()
        val rawToken = awaitInvitationFor(email).rawToken.value
        activateAccount.execute(rawToken, password).shouldBeRight()
    }

    private fun awaitInvitationFor(email: String): InvitationEmailRequested =
        await { emailSender.sent.firstOrNull { it.to.value == email } }
            ?: throw AssertionError("No se entregó el email de invitación para $email en 5 s")

    private fun awaitMagicLinkFor(email: String): MagicLinkEmailRequested =
        await { emailSender.magicLinksSent.firstOrNull { it.to.value == email } }
            ?: throw AssertionError("No se entregó el email de magic link para $email en 5 s")

    private fun <T> await(supplier: () -> T?): T? {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            supplier()?.let { return it }
            Thread.sleep(25)
        }
        return null
    }
}
