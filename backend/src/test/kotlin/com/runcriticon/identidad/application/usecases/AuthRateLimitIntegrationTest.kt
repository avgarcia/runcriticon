package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.MagicLinkEmailRequested
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.infrastructure.persistence.AuditEventEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.InvitationEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.MagicLinkEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.PasswordHistoryEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.UserEntityRepository
import com.runcriticon.identidad.infrastructure.rest.CredentialsRequest
import com.runcriticon.identidad.infrastructure.rest.SessionController
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.UUID

/**
 * Integración del rate-limiting de autenticación (ADR-0003 D12, LAL-35) sobre Postgres real y el
 * adaptador de límites **real** (no neutralizado). Verifica: 429 con `Retry-After` al reintentar un
 * login fallido dentro de la ventana de backoff; respuesta neutra + asiento `MAGIC_LINK_RATE_LIMITED`
 * al reincidir en el mismo email antes del cooldown; y 429 al superar el límite por actor de
 * invitaciones (bajado a 2/h por configuración de test). Reutiliza [FakeEmailConfig]/[FakeEmailSender].
 */
@SpringBootTest
@Testcontainers
@Import(FakeEmailConfig::class)
class AuthRateLimitIntegrationTest {
    @Autowired private lateinit var inviteCoach: InviteCoach

    @Autowired private lateinit var activateAccount: ActivateAccount

    @Autowired private lateinit var requestMagicLink: RequestMagicLink

    @Autowired private lateinit var sessionController: SessionController

    @Autowired private lateinit var userEntityRepository: UserEntityRepository

    @Autowired private lateinit var invitationEntityRepository: InvitationEntityRepository

    @Autowired private lateinit var magicLinkEntityRepository: MagicLinkEntityRepository

    @Autowired private lateinit var passwordHistoryEntityRepository: PasswordHistoryEntityRepository

    @Autowired private lateinit var auditEventEntityRepository: AuditEventEntityRepository

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
            // Bajamos el límite por actor para ejercitar el 429 sin emitir 100 invitaciones.
            registry.add("runcriticon.identidad.ratelimit.invitation-per-actor-hourly") { "2" }
        }
    }

    private val clubId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
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
    fun `el segundo login fallido dentro de la ventana responde 429 con Retry-After`() {
        val ip = "198.51.100.5"
        val bad = CredentialsRequest(email = "bruteforce@club.test", password = "mal-mal-mal")

        val first = login(bad, ip)
        first.statusCode shouldBe HttpStatus.UNAUTHORIZED

        val second = login(bad, ip)
        second.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
        second.headers.getFirst(HttpHeaders.RETRY_AFTER).shouldBeInstanceOf<String>()
    }

    @Test
    fun `pedir dos magic links seguidos al mismo email deja un asiento MAGIC_LINK_RATE_LIMITED y no reenvia`() {
        val ip = "203.0.113.20"
        seedActiveCoach("cooldown@club.test")

        requestMagicLink.execute(clubId, "cooldown@club.test", ip).shouldBeRight()
        awaitMagicLinkFor("cooldown@club.test")

        // Segunda petición inmediata: cae en el cooldown → respuesta neutra, sin reenvío.
        requestMagicLink.execute(clubId, "cooldown@club.test", ip).shouldBeRight()

        emailSender.magicLinksSent.count { it.to.value == "cooldown@club.test" } shouldBe 1
        val rateLimited =
            auditEventEntityRepository.findAll().filter { it.type == "MAGIC_LINK_RATE_LIMITED" }
        rateLimited.size shouldBe 1
        rateLimited.single().metadata?.get("ip") shouldBe ip
        rateLimited
            .single()
            .metadata
            ?.get("email_hash")
            .shouldBeInstanceOf<String>()
    }

    @Test
    fun `superar el limite por actor de invitaciones devuelve RateLimited`() {
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ADMIN)

        inviteCoach.execute(admin, "Uno", "uno@club.test").shouldBeRight()
        inviteCoach.execute(admin, "Dos", "dos@club.test").shouldBeRight()
        inviteCoach
            .execute(admin, "Tres", "tres@club.test")
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.RateLimited>()
    }

    private fun login(
        credentials: CredentialsRequest,
        ip: String,
    ) = sessionController.login(
        credentials,
        MockHttpServletRequest().apply { remoteAddr = ip },
        MockHttpServletResponse(),
    )

    private fun seedActiveCoach(email: String) {
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ADMIN)
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
