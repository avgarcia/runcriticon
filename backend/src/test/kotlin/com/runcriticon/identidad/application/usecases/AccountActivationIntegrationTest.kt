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
import java.util.UUID

/**
 * Integración de la activación de cuenta (LAL-9) sobre Postgres real (Testcontainers): un alumno
 * invitado consume su token, fija contraseña y pasa a ACTIVO con sesión (Principal). Cubre también el
 * token reutilizado (Conflict) y la contraseña que incumple la política (la cuenta sigue INVITADO).
 * Reutiliza [FakeEmailConfig]/[FakeEmailSender] de [CoachInvitationIntegrationTest] (mismo paquete).
 */
@SpringBootTest
@Testcontainers
@Import(FakeEmailConfig::class)
class AccountActivationIntegrationTest {
    @Autowired private lateinit var inviteStudent: InviteStudent

    @Autowired private lateinit var activateAccount: ActivateAccount

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
            registry.add("runcriticon.observability.userid-hash-salt") { "test-userid-hash-salt-not-prod" }
        }
    }

    private val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val coach = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ENTRENADOR)
    private val validPassword = "clave-clave-clave"

    @BeforeEach
    fun limpiar() {
        passwordHistoryEntityRepository.deleteAll()
        invitationEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
        emailSender.sent.clear()
    }

    @Test
    fun `un alumno invitado activa su cuenta y pasa a ACTIVO con sesión consumiendo la invitación (CA 1)`() {
        inviteStudent.execute(coach, "Marta Ruiz", "marta@club.test").shouldBeRight()
        val rawToken = awaitInvitationFor("marta@club.test").rawToken.value

        val principal = activateAccount.execute(rawToken, validPassword).shouldBeRight()
        principal.role shouldBe Role.ALUMNO

        val user = userRepository.findByEmail(clubId, Email.of("marta@club.test")).shouldNotBeNull()
        user.status shouldBe UserStatus.ACTIVO
        user.passwordHash.shouldNotBeNull()

        passwordHistoryEntityRepository.count() shouldBe 1
        val invitation = invitationEntityRepository.findTopByUserIdOrderByIssuedAtDesc(user.id.value).shouldNotBeNull()
        invitation.consumedAt.shouldNotBeNull()
    }

    @Test
    fun `reutilizar el token ya consumido devuelve Conflict (CA 2)`() {
        inviteStudent.execute(coach, "Marta Ruiz", "marta@club.test").shouldBeRight()
        val rawToken = awaitInvitationFor("marta@club.test").rawToken.value
        activateAccount.execute(rawToken, validPassword).shouldBeRight()

        activateAccount
            .execute(rawToken, "otra-contrasena-9876")
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.Conflict>()
    }

    @Test
    fun `una contraseña que incumple la política se rechaza y la cuenta sigue INVITADO`() {
        inviteStudent.execute(coach, "Marta Ruiz", "marta@club.test").shouldBeRight()
        val rawToken = awaitInvitationFor("marta@club.test").rawToken.value

        activateAccount
            .execute(rawToken, "corta")
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.InvalidInput>()

        val user = userRepository.findByEmail(clubId, Email.of("marta@club.test")).shouldNotBeNull()
        user.status shouldBe UserStatus.INVITADO
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
