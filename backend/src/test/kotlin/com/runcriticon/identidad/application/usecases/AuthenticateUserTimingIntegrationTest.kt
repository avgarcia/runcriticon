package com.runcriticon.identidad.application.usecases
import com.runcriticon.identidad.application.ports.inbound.InvitationEmailRequested
import com.runcriticon.identidad.application.usecases.account.ActivateAccountCommand
import com.runcriticon.identidad.application.usecases.authentication.AuthenticateUserCommand
import com.runcriticon.identidad.application.usecases.invitation.InviteCoachCommand
import com.runcriticon.identidad.infrastructure.persistence.repositories.AuditEventEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.InvitationEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.MagicLinkEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.PasswordHistoryEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.doubles.shouldBeLessThan
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
import java.util.UUID

private const val SAMPLE_SIZE = 20
private const val MAX_RATIO = 1.5

/**
 * Verifica el AC de LAL-36 (ADR-0003 D5): un login con email inexistente y uno con contraseña
 * incorrecta sobre un usuario real tardan tiempos comparables. Necesita el [Argon2PasswordHasher]
 * real (no mock), así que va contra Postgres real (Testcontainers), igual que
 * [AuthRateLimitIntegrationTest]. Autowirea [AuthenticateUserCommand] directamente, no [SessionController]:
 * el throttling (LAL-35) vive en el controller, no en el caso de uso, y repetir logins seguidos
 * chocaría con el backoff.
 */
@SpringBootTest
@Testcontainers
@Import(FakeEmailConfig::class)
class AuthenticateUserTimingIntegrationTest {
    @Autowired private lateinit var authenticateUser: AuthenticateUserCommand

    @Autowired private lateinit var inviteCoach: InviteCoachCommand

    @Autowired private lateinit var activateAccount: ActivateAccountCommand

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
        }
    }

    private val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
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
    fun `login con usuario inexistente y con password incorrecta sobre usuario real tardan tiempos comparables`() {
        seedActiveCoach("timing@club.test")

        val tiemposUsuarioExistente =
            (1..SAMPLE_SIZE).map { i ->
                measureNanos { authenticateUser.execute(clubId, "timing@club.test", "password-mala-$i") }
            }
        val tiemposUsuarioInexistente =
            (1..SAMPLE_SIZE).map { i ->
                measureNanos { authenticateUser.execute(clubId, "no-existe-$i@club.test", "cualquier-cosa") }
            }

        val promedioExistente = tiemposUsuarioExistente.average()
        val promedioInexistente = tiemposUsuarioInexistente.average()
        val ratio = maxOf(promedioExistente, promedioInexistente) / minOf(promedioExistente, promedioInexistente)

        ratio shouldBeLessThan MAX_RATIO
    }

    private fun <T> measureNanos(block: () -> T): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun seedActiveCoach(email: String) {
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)
        inviteCoach.execute(admin, "Ana Coach", email).shouldBeRight()
        val rawToken = awaitInvitationFor(email).rawToken.value
        activateAccount.execute(rawToken, password, false, null, "203.0.113.10", "junit-agent/1.0").shouldBeRight()
    }

    private fun awaitInvitationFor(email: String): InvitationEmailRequested =
        await { emailSender.sent.firstOrNull { it.to.value == email } }
            ?: throw AssertionError("No se entregó el email de invitación para $email en 5 s")

    private fun <T> await(supplier: () -> T?): T? {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            supplier()?.let { return it }
            Thread.sleep(25)
        }
        return null
    }
}
