package com.runcriticon.identidad.application.usecases
import com.runcriticon.identidad.application.ports.inbound.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.inbound.MagicLinkEmailRequested
import com.runcriticon.identidad.application.ports.inbound.PasswordResetEmailRequested
import com.runcriticon.identidad.application.ports.outbound.security.TokenHasher
import com.runcriticon.identidad.application.usecases.account.ActivateAccountCommand
import com.runcriticon.identidad.application.usecases.invitation.InviteCoachCommand
import com.runcriticon.identidad.application.usecases.magiclink.ConsumeMagicLinkCommand
import com.runcriticon.identidad.application.usecases.magiclink.RequestMagicLinkCommand
import com.runcriticon.identidad.application.usecases.password.ConsumePasswordResetCommand
import com.runcriticon.identidad.application.usecases.password.RequestPasswordResetCommand
import com.runcriticon.identidad.domain.audit.AuditEventType
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
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Integración del reseteo de contraseña (LAL-12, ADR-0003 D8) sobre Postgres real (Testcontainers).
 * Ejercita el recorrido completo y los casos críticos de la tabla de tests de ADR-0003 (D8):
 *  - request→consumo extremo a extremo (email entregado vía outbox → fija contraseña nueva → auto-login);
 *  - **invalidación de sesiones (D8)**: se crean filas en SPRING_SESSION indexadas por el usuario, se
 *    resetea y se afirma que desaparecen (una sesión robada no sobrevive al reseteo);
 *  - la nueva contraseña cumple la política D6;
 *  - un solo uso + caducidad 15 min;
 *  - **aislamiento de propósito**: un token de reseteo no vale en el endpoint de login y viceversa;
 *  - respuesta neutra ante email inexistente.
 * Reutiliza [FakeEmailConfig]/[FakeEmailSender] de [CoachInvitationIntegrationTest] (mismo paquete).
 */
@SpringBootTest
@Testcontainers
@Import(FakeEmailConfig::class, UnlimitedRateLimitConfig::class)
class PasswordResetIntegrationTest {
    @Autowired private lateinit var inviteCoach: InviteCoachCommand

    @Autowired private lateinit var activateAccount: ActivateAccountCommand

    @Autowired private lateinit var requestMagicLink: RequestMagicLinkCommand

    @Autowired private lateinit var consumeMagicLink: ConsumeMagicLinkCommand

    @Autowired private lateinit var requestPasswordReset: RequestPasswordResetCommand

    @Autowired private lateinit var consumePasswordReset: ConsumePasswordResetCommand

    @Autowired private lateinit var userEntityRepository: UserEntityRepository

    @Autowired private lateinit var invitationEntityRepository: InvitationEntityRepository

    @Autowired private lateinit var magicLinkEntityRepository: MagicLinkEntityRepository

    @Autowired private lateinit var passwordHistoryEntityRepository: PasswordHistoryEntityRepository

    @Autowired private lateinit var auditEventEntityRepository: AuditEventEntityRepository

    @Autowired private lateinit var tokenHasher: TokenHasher

    @Autowired private lateinit var emailSender: FakeEmailSender

    @Autowired private lateinit var sessionRepository: SessionRepository<out Session>

    @Autowired private lateinit var indexedSessionRepository: FindByIndexNameSessionRepository<out Session>

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
    private val newPassword = "clave-clave-nueva"

    @BeforeEach
    fun limpiar() {
        magicLinkEntityRepository.deleteAll()
        invitationEntityRepository.deleteAll()
        passwordHistoryEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
        auditEventEntityRepository.deleteAll()
        emailSender.sent.clear()
        emailSender.magicLinksSent.clear()
        emailSender.passwordResetsSent.clear()
    }

    @Test
    fun `reseteo extremo a extremo fija contrasena nueva, audita e inicia sesion (CA D8)`() {
        val userId = seedActiveCoach("ana@club.test")

        requestPasswordReset.execute(clubId, "ana@club.test", "203.0.113.2").shouldBeRight()
        val rawToken = awaitResetFor("ana@club.test").rawToken.value

        val principal = consumePasswordReset.execute(rawToken, newPassword).shouldBeRight()
        principal.userId shouldBe userId
        principal.role shouldBe Role.ENTRENADOR

        // La solicitud dejó asiento RESETEO_INICIADO; el consumo, PASSWORD_CAMBIADA.
        val tipos = auditEventEntityRepository.findAll().map { it.type }
        (AuditEventType.RESETEO_INICIADO.name in tipos) shouldBe true
        (AuditEventType.PASSWORD_CAMBIADA.name in tipos) shouldBe true

        // Un solo uso: reusar el mismo token de reseteo se rechaza.
        consumePasswordReset
            .execute(rawToken, newPassword)
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.Conflict>()
    }

    @Test
    fun `el reseteo invalida todas las sesiones activas del usuario (D8 critico)`() {
        val userId = seedActiveCoach("ana@club.test")

        // Se crean dos sesiones para el usuario y una de otro usuario (que NO debe tocarse), indexadas
        // por PRINCIPAL_NAME = userId, igual que las que crea SecuritySessionManager al iniciar sesión.
        crearSesionIndexada(userId)
        crearSesionIndexada(userId)
        val otroUserId = UUID.randomUUID()
        crearSesionIndexada(otroUserId)

        indexedSessionRepository.findByPrincipalName(userId.toString()).size shouldBe 2

        requestPasswordReset.execute(clubId, "ana@club.test", "203.0.113.2").shouldBeRight()
        val rawToken = awaitResetFor("ana@club.test").rawToken.value
        consumePasswordReset.execute(rawToken, newPassword).shouldBeRight()

        // D8: tras el reseteo, las sesiones del usuario desaparecen; las de otros usuarios permanecen.
        indexedSessionRepository.findByPrincipalName(userId.toString()).size shouldBe 0
        indexedSessionRepository.findByPrincipalName(otroUserId.toString()).size shouldBe 1
    }

    @Test
    fun `un reseteo con contrasena que incumple la politica D6 se rechaza`() {
        seedActiveCoach("ana@club.test")
        requestPasswordReset.execute(clubId, "ana@club.test", "203.0.113.2").shouldBeRight()
        val rawToken = awaitResetFor("ana@club.test").rawToken.value

        // Demasiado corta (< 12): política D6.
        consumePasswordReset
            .execute(rawToken, "corta")
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.InvalidInput>()
    }

    @Test
    fun `un token de reseteo caducado (mas de 15 min) se rechaza`() {
        seedActiveCoach("ana@club.test")
        requestPasswordReset.execute(clubId, "ana@club.test", "203.0.113.2").shouldBeRight()
        val rawToken = awaitResetFor("ana@club.test").rawToken.value

        val stored =
            magicLinkEntityRepository.findByTokenHash(tokenHasher.hash(RawToken(rawToken)).value).shouldNotBeNull()
        stored.expiresAt = Instant.now().minus(Duration.ofMinutes(1))
        magicLinkEntityRepository.save(stored)

        consumePasswordReset
            .execute(rawToken, newPassword)
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.InvalidInput>()
    }

    @Test
    fun `aislamiento de proposito - un token de reseteo no vale en el consumo de login`() {
        seedActiveCoach("ana@club.test")
        requestPasswordReset.execute(clubId, "ana@club.test", "203.0.113.2").shouldBeRight()
        val resetToken = awaitResetFor("ana@club.test").rawToken.value

        // El endpoint de login (ConsumeMagicLinkCommand) rechaza un token emitido para RESETEO.
        consumeMagicLink
            .execute(resetToken)
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.InvalidInput>()
    }

    @Test
    fun `aislamiento de proposito - un token de login no vale en el consumo de reseteo`() {
        seedActiveCoach("ana@club.test")
        requestMagicLink.execute(clubId, "ana@club.test", "203.0.113.2").shouldBeRight()
        val loginToken = awaitMagicLinkFor("ana@club.test").rawToken.value

        // El endpoint de reseteo (ConsumePasswordResetCommand) rechaza un token emitido para LOGIN.
        consumePasswordReset
            .execute(loginToken, newPassword)
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.InvalidInput>()
    }

    @Test
    fun `un email inexistente no emite reseteo (respuesta neutra)`() {
        requestPasswordReset.execute(clubId, "nadie@club.test", "203.0.113.2").shouldBeRight()

        magicLinkEntityRepository.count() shouldBe 0
        emailSender.passwordResetsSent.none { it.to.value == "nadie@club.test" } shouldBe true
    }

    private fun crearSesionIndexada(userId: UUID) {
        val session = sessionRepository.createSession()
        // Índice por principal name: lo que consulta FindByIndexNameSessionRepository.findByPrincipalName.
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, userId.toString())
        @Suppress("UNCHECKED_CAST")
        (sessionRepository as SessionRepository<Session>).save(session)
    }

    private fun seedActiveCoach(email: String): UUID {
        val coachId = inviteCoach.execute(admin, "Ana Coach", email).shouldBeRight()
        val rawToken = awaitInvitationFor(email).rawToken.value
        activateAccount.execute(rawToken, password).shouldBeRight()
        return coachId.value
    }

    private fun awaitInvitationFor(email: String): InvitationEmailRequested =
        await { emailSender.sent.firstOrNull { it.to.value == email } }
            ?: throw AssertionError("No se entregó el email de invitación para $email en 5 s")

    private fun awaitMagicLinkFor(email: String): MagicLinkEmailRequested =
        await { emailSender.magicLinksSent.firstOrNull { it.to.value == email } }
            ?: throw AssertionError("No se entregó el email de magic link para $email en 5 s")

    private fun awaitResetFor(email: String): PasswordResetEmailRequested =
        await { emailSender.passwordResetsSent.firstOrNull { it.to.value == email } }
            ?: throw AssertionError("No se entregó el email de reseteo para $email en 5 s")

    private fun <T> await(supplier: () -> T?): T? {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            supplier()?.let { return it }
            Thread.sleep(25)
        }
        return null
    }
}
