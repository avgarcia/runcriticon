package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.identidad.infrastructure.persistence.InvitationEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.MagicLinkEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.PasswordHistoryEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.UserEntityRepository
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.UUID

/**
 * Integración de la gestión de sesión por admin (LAL-13, ADR-0003 D11) sobre Postgres real
 * (Testcontainers). Cubre los casos críticos de D11:
 *  - **el admin revoca a un usuario** → todas sus filas en SPRING_SESSION desaparecen;
 *  - **desactivar** → la cuenta pasa a `DESACTIVADO` y sus sesiones se cierran;
 *  - un rol **no ADMIN** que intenta revocar → `Forbidden` (403 en la capa api).
 * Reutiliza [FakeEmailConfig]/[FakeEmailSender] de [CoachInvitationIntegrationTest] (mismo paquete).
 */
@SpringBootTest
@Testcontainers
@Import(FakeEmailConfig::class)
class AdminSessionManagementIntegrationTest {
    @Autowired private lateinit var inviteCoach: InviteCoach

    @Autowired private lateinit var activateAccount: ActivateAccount

    @Autowired private lateinit var revokeUserSessions: RevokeUserSessions

    @Autowired private lateinit var deactivateUser: DeactivateUser

    @Autowired private lateinit var listCoaches: ListCoaches

    @Autowired private lateinit var userEntityRepository: UserEntityRepository

    @Autowired private lateinit var invitationEntityRepository: InvitationEntityRepository

    @Autowired private lateinit var magicLinkEntityRepository: MagicLinkEntityRepository

    @Autowired private lateinit var passwordHistoryEntityRepository: PasswordHistoryEntityRepository

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

    private val clubId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val admin = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ADMIN)
    private val password = "clave-clave-clave"

    @BeforeEach
    fun limpiar() {
        magicLinkEntityRepository.deleteAll()
        invitationEntityRepository.deleteAll()
        passwordHistoryEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
        // AuthScopeEnforcementAspect (ADR-0009 D11) verifica el clubId de @AuthScope(CLUB) contra el
        // principal de SecurityContextHolder; estos tests invocan los casos de uso directamente (sin
        // pasar por login HTTP), así que hay que sembrar el contexto igual que haría SecuritySessionManager.
        val authentication =
            UsernamePasswordAuthenticationToken(admin, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
    }

    @AfterEach
    fun limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `el admin revoca todas las sesiones activas de un usuario`() {
        val coachId = seedActiveCoach("ana@club.test")
        crearSesionIndexada(coachId)
        indexedSessionRepository.findByPrincipalName(coachId.toString()).size shouldBe 1

        revokeUserSessions.execute(admin, UserId.of(coachId)).shouldBeRight()

        indexedSessionRepository.findByPrincipalName(coachId.toString()).size shouldBe 0
    }

    @Test
    fun `desactivar una cuenta la marca DESACTIVADO y cierra sus sesiones`() {
        val coachId = seedActiveCoach("bea@club.test")
        crearSesionIndexada(coachId)

        deactivateUser.execute(admin, UserId.of(coachId)).shouldBeRight()

        indexedSessionRepository.findByPrincipalName(coachId.toString()).size shouldBe 0
        val coaches = listCoaches.execute(admin).shouldBeRight()
        coaches.first { it.id == coachId }.status shouldBe UserStatus.DESACTIVADO
    }

    @Test
    fun `un entrenador no puede revocar sesiones de otro (Forbidden)`() {
        val coachId = seedActiveCoach("ces@club.test")
        val coachPrincipal = Principal(userId = coachId, clubId = clubId, role = Role.ENTRENADOR)

        revokeUserSessions
            .execute(coachPrincipal, UserId.of(coachId))
            .shouldBeLeft(IdentidadError.Forbidden)
    }

    private fun crearSesionIndexada(userId: UUID) {
        val session = sessionRepository.createSession()
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

    private fun <T> await(supplier: () -> T?): T? {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            supplier()?.let { return it }
            Thread.sleep(25)
        }
        return null
    }
}
