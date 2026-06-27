package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.identidad.infrastructure.persistence.InvitationEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.PasswordHistoryEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.UserEntityRepository
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
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
 * Integración de la caducidad de contraseña (LAL-10, ADR-0003 D7) sobre Postgres real
 * (Testcontainers): un alumno activa su cuenta, se fuerza la caducidad en BD y se comprueba el
 * recorrido completo: login con la contraseña caducada -> PasswordExpired (sin sesión), cambio
 * forzado -> Principal, y login normal con la nueva. Verifica además que el cambio respeta el
 * histórico D6. Reutiliza [FakeEmailConfig]/[FakeEmailSender] de [CoachInvitationIntegrationTest].
 */
@SpringBootTest
@Testcontainers
@Import(FakeEmailConfig::class)
class PasswordExpiryIntegrationTest {
    @Autowired private lateinit var inviteStudent: InviteStudent

    @Autowired private lateinit var activateAccount: ActivateAccount

    @Autowired private lateinit var authenticateUser: AuthenticateUser

    @Autowired private lateinit var changeExpiredPassword: ChangeExpiredPassword

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var userEntityRepository: UserEntityRepository

    @Autowired private lateinit var invitationEntityRepository: InvitationEntityRepository

    @Autowired private lateinit var passwordHistoryEntityRepository: PasswordHistoryEntityRepository

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
        }
    }

    private val clubId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val coach = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ENTRENADOR)
    private val oldPassword = "clave-clave-clave"
    private val newPassword = "fresca-fresca-2026"

    @BeforeEach
    fun limpiar() {
        passwordHistoryEntityRepository.deleteAll()
        invitationEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
        emailSender.sent.clear()
    }

    @Test
    fun `contrasena caducada exige cambio y, tras cambiarla, se entra (CA 3)`() {
        seedActiveExpiredStudent("marta@club.test", oldPassword)

        // Login: credenciales correctas pero contraseña caducada -> PasswordExpired (no se crea sesión).
        authenticateUser.execute(clubId, "marta@club.test", oldPassword).shouldBeRight() shouldBe
            LoginOutcome.PasswordExpired

        // Cambio forzado con una contraseña nueva válida -> Principal (auto-login).
        val principal =
            changeExpiredPassword.execute(clubId, "marta@club.test", oldPassword, newPassword).shouldBeRight()
        principal.role shouldBe Role.ALUMNO

        // Tras el cambio: cuenta ACTIVO, ya no caducada, e histórico con dos entradas (activación + cambio).
        val user = userRepository.findByEmail(clubId, Email.of("marta@club.test")).shouldNotBeNull()
        user.status shouldBe UserStatus.ACTIVO
        user.isPasswordExpired(Instant.now()) shouldBe false
        passwordHistoryEntityRepository.count() shouldBe 2

        // Y el login normal con la contraseña nueva ya funciona.
        authenticateUser
            .execute(clubId, "marta@club.test", newPassword)
            .shouldBeRight()
            .shouldBeInstanceOf<LoginOutcome.Authenticated>()
    }

    @Test
    fun `el cambio forzado rechaza reutilizar una contrasena anterior (D6 historico)`() {
        seedActiveExpiredStudent("marta@club.test", oldPassword)

        changeExpiredPassword
            .execute(clubId, "marta@club.test", oldPassword, oldPassword)
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.InvalidInput>()
    }

    private fun seedActiveExpiredStudent(
        email: String,
        password: String,
    ) {
        inviteStudent.execute(coach, "Ana Pinares", email).shouldBeRight()
        val rawToken = awaitInvitationFor(email).rawToken.value
        activateAccount.execute(rawToken, password).shouldBeRight()

        val user = userRepository.findByEmail(clubId, Email.of(email)).shouldNotBeNull()
        val entity = userEntityRepository.findById(user.id.value).orElseThrow()
        entity.passwordUpdatedAt = Instant.now().minus(Duration.ofDays(91))
        userEntityRepository.save(entity)
    }

    private fun awaitInvitationFor(email: String): InvitationEmailRequested {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            emailSender.sent.firstOrNull { it.to.value == email }?.let { return it }
            Thread.sleep(25)
        }
        throw AssertionError("No se entregó el email de invitación para $email en 5 s")
    }
}
