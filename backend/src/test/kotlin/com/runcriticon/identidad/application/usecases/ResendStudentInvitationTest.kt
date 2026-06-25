package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.InvitationRepository
import com.runcriticon.identidad.application.ports.TokenGenerator
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class ResendStudentInvitationTest :
    FunSpec({
        val club = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val admin = Principal(userId = UUID.randomUUID(), clubId = club, role = Role.ADMIN)
        val coach = Principal(userId = UUID.randomUUID(), clubId = club, role = Role.ENTRENADOR)
        val studentId = UserId.new()

        val userRepository = mockk<UserRepository>(relaxed = true)
        val invitationRepository = mockk<InvitationRepository>(relaxed = true)
        val tokenGenerator = mockk<TokenGenerator>()
        val tokenHasher = mockk<TokenHasher>()
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val useCase =
            ResendStudentInvitation(
                userRepository,
                invitationRepository,
                tokenGenerator,
                tokenHasher,
                auditTrail,
                eventPublisher,
            )

        val invitadoStudent =
            User(
                id = studentId,
                clubId = club,
                email = Email.of("marta@club.local"),
                name = "Marta",
                role = Role.ALUMNO,
                passwordHash = null,
                status = UserStatus.INVITADO,
            )

        val existingInvitation =
            Invitation.issue(
                userId = studentId,
                clubId = club,
                tokenHash = TokenHash("old-hash"),
                now = Instant.now(),
            )

        beforeTest {
            clearMocks(userRepository, invitationRepository, tokenGenerator, tokenHasher, auditTrail, eventPublisher)
            every { tokenGenerator.generate() } returns RawToken("new-raw")
            every { tokenHasher.hash(any()) } returns TokenHash("new-hash")
            every { userRepository.findById(club, studentId) } returns invitadoStudent
            every { invitationRepository.findLatestByUserId(studentId) } returns existingInvitation
        }

        test("admin reenvía: invalida la anterior, guarda la nueva, publica email y audita") {
            val savedInvitations = mutableListOf<Invitation>()
            val eventSlot = slot<Any>()
            val auditSlot = slot<AuditEntry>()
            every { invitationRepository.save(capture(savedInvitations)) } returns Unit
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            useCase.execute(admin, studentId).shouldBeRight()

            savedInvitations.size shouldBe 2
            val invalidated = savedInvitations.first()
            val fresh = savedInvitations.last()
            invalidated.consumedAt.shouldNotBeNull()
            fresh.consumedAt shouldBe null
            fresh.tokenHash shouldBe TokenHash("new-hash")

            val email = eventSlot.captured.shouldBeInstanceOf<InvitationEmailRequested>()
            email.rawToken shouldBe RawToken("new-raw")
            email.to shouldBe invitadoStudent.email

            val audit = auditSlot.captured
            audit.type shouldBe AuditEventType.INVITACION_EMITIDA
            audit.actorId shouldBe admin.userId
            audit.subjectId shouldBe invitadoStudent.id.value
        }

        test("un entrenador también puede reenviar (delegación, ADR-0003 D3)") {
            useCase.execute(coach, studentId).shouldBeRight()

            verify(exactly = 2) { invitationRepository.save(any()) }
        }

        test("un ALUMNO no puede reenviar: Forbidden y no guarda nada") {
            val student = Principal(userId = UUID.randomUUID(), clubId = club, role = Role.ALUMNO)

            useCase.execute(student, studentId).shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { invitationRepository.save(any()) }
        }

        test("usuario no encontrado devuelve NotFound y no guarda nada") {
            every { userRepository.findById(club, studentId) } returns null

            useCase.execute(admin, studentId).shouldBeLeft(IdentidadError.NotFound)

            verify(exactly = 0) { invitationRepository.save(any()) }
        }

        test("un id que no es de un alumno (p. ej. entrenador) devuelve NotFound") {
            every { userRepository.findById(club, studentId) } returns invitadoStudent.copy(role = Role.ENTRENADOR)

            useCase.execute(admin, studentId).shouldBeLeft(IdentidadError.NotFound)

            verify(exactly = 0) { invitationRepository.save(any()) }
        }

        test("usuario en estado ACTIVO devuelve Conflict y no guarda nada") {
            every { userRepository.findById(club, studentId) } returns invitadoStudent.copy(status = UserStatus.ACTIVO)

            useCase
                .execute(admin, studentId)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { invitationRepository.save(any()) }
        }

        test("sin invitación previa devuelve Conflict") {
            every { invitationRepository.findLatestByUserId(studentId) } returns null

            useCase
                .execute(admin, studentId)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { invitationRepository.save(any()) }
        }
    })
