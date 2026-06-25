package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.identidad.infrastructure.persistence.InvitationEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.UserEntityRepository
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
 * Integración del alta y la reinvitación de alumno (LAL-8) sobre Postgres real (Testcontainers):
 * un ENTRENADOR delega el alta, el alumno se crea `INVITADO` con rol `ALUMNO`, la invitación se
 * persiste, el email se entrega vía outbox de Spring Modulith y la reinvitación rota el token.
 * Reutiliza [FakeEmailConfig]/[FakeEmailSender] de [CoachInvitationIntegrationTest] (mismo paquete)
 * para no contactar con Postmark.
 *
 * Cruces: ADR-0003 D3 (delegación a entrenadores), D4 (rotación del token), ADR-0009 (RBAC),
 * ADR-0010 (Testcontainers).
 */
@SpringBootTest
@Testcontainers
@Import(FakeEmailConfig::class)
class StudentInvitationIntegrationTest {
    @Autowired private lateinit var inviteStudent: InviteStudent

    @Autowired private lateinit var resendStudentInvitation: ResendStudentInvitation

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var userEntityRepository: UserEntityRepository

    @Autowired private lateinit var invitationEntityRepository: InvitationEntityRepository

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

    @BeforeEach
    fun limpiar() {
        invitationEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
        emailSender.sent.clear()
    }

    @Test
    fun `un entrenador da de alta un alumno INVITADO con rol ALUMNO y se entrega el email (CA 1, ADR-0003 D3)`() {
        val userId = inviteStudent.execute(coach, "Marta Ruiz", "marta@club.test").shouldBeRight()

        val stored = userRepository.findById(clubId, userId).shouldNotBeNull()
        stored.role shouldBe Role.ALUMNO
        stored.status shouldBe UserStatus.INVITADO

        val email = awaitInvitationFor("marta@club.test")
        email.recipientName shouldBe "Marta Ruiz"
    }

    @Test
    fun `un ALUMNO no puede dar de alta a otro alumno (ADR-0009) y no deja rastro`() {
        val student = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ALUMNO)

        inviteStudent.execute(student, "Intruso", "intruso@club.test").shouldBeLeft(IdentidadError.Forbidden)

        userEntityRepository.count() shouldBe 0
        invitationEntityRepository.count() shouldBe 0
    }

    @Test
    fun `email ya existente en el club rechaza el alta sin duplicar fila (CA 3)`() {
        inviteStudent.execute(coach, "Marta Ruiz", "marta@club.test").shouldBeRight()

        inviteStudent
            .execute(coach, "Marta Duplicada", "marta@club.test")
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.Conflict>()

        userEntityRepository.count() shouldBe 1
        invitationEntityRepository.count() shouldBe 1
    }

    @Test
    fun `un entrenador reinvita a un alumno: emite token nuevo e invalida el anterior (ADR-0003 D4)`() {
        val studentId = inviteStudent.execute(coach, "Marta Ruiz", "marta@club.test").shouldBeRight()
        val original = invitationEntityRepository.findTopByUserIdOrderByIssuedAtDesc(studentId.value).shouldNotBeNull()
        original.consumedAt.shouldBeNull()

        resendStudentInvitation.execute(coach, studentId).shouldBeRight()

        val invitations = invitationEntityRepository.findAll().filter { it.userId == studentId.value }
        invitations.size shouldBe 2
        val fresh = invitations.single { it.consumedAt == null }
        val invalidated = invitations.single { it.consumedAt != null }
        invalidated.id shouldBe original.id
        fresh.tokenHash shouldNotBe invalidated.tokenHash
    }

    /** Espera (poll corto) a que el adaptador de email reciba la invitación del destinatario indicado. */
    private fun awaitInvitationFor(email: String): InvitationEmailRequested {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            emailSender.sent.firstOrNull { it.to.value == email }?.let { return it }
            Thread.sleep(25)
        }
        throw AssertionError("No se entregó el email de invitación para $email en 5 s")
    }
}
