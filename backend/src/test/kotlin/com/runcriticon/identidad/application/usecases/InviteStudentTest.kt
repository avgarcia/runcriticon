package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.api.events.AlumnoInvitado
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.InvitationRepository
import com.runcriticon.identidad.application.ports.TokenGenerator
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class InviteStudentTest :
    FunSpec({
        val club = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val admin = Principal(userId = UUID.randomUUID(), clubId = club, role = Role.ADMIN)
        val coach = Principal(userId = UUID.randomUUID(), clubId = club, role = Role.ENTRENADOR)

        val userRepository = mockk<UserRepository>(relaxed = true)
        val invitationRepository = mockk<InvitationRepository>(relaxed = true)
        val tokenGenerator = mockk<TokenGenerator>()
        val tokenHasher = mockk<TokenHasher>()
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val useCase =
            InviteStudent(userRepository, invitationRepository, tokenGenerator, tokenHasher, auditTrail, eventPublisher)

        beforeTest {
            clearMocks(userRepository, invitationRepository, tokenGenerator, tokenHasher, auditTrail, eventPublisher)
            every { tokenGenerator.generate() } returns RawToken("raw-token")
            every { tokenHasher.hash(any()) } returns TokenHash("hashed")
            every { userRepository.findByEmail(any(), any()) } returns null
        }

        test("admin invita: crea alumno INVITADO, emite invitación, publica email + AlumnoInvitado y audita") {
            val userSlot = slot<User>()
            val events = mutableListOf<Any>()
            val auditSlot = slot<AuditEntry>()
            every { userRepository.save(capture(userSlot)) } returns Unit
            every { eventPublisher.publishEvent(capture(events)) } returns Unit
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            val createdId = useCase.execute(admin, "Marta", "Marta@Club.local").shouldBeRight()

            val saved = userSlot.captured
            createdId shouldBe saved.id
            saved.clubId shouldBe club
            saved.role shouldBe Role.ALUMNO
            saved.status shouldBe UserStatus.INVITADO
            saved.passwordHash shouldBe null
            saved.email shouldBe Email.of("marta@club.local")

            verify { invitationRepository.save(any()) }

            val email = events.filterIsInstance<InvitationEmailRequested>().single()
            email.to shouldBe Email.of("marta@club.local")
            email.recipientName shouldBe "Marta"
            email.rawToken shouldBe RawToken("raw-token")

            val published = events.filterIsInstance<AlumnoInvitado>().single()
            published.aggregateId shouldBe saved.id.value
            published.clubId shouldBe club
            published.actorId shouldBe admin.userId
            published.version shouldBe 1
            published.name shouldBe "Marta"
            published.email shouldBe "marta@club.local"

            auditSlot.captured.type shouldBe AuditEventType.INVITACION_EMITIDA
            auditSlot.captured.actorId shouldBe admin.userId
            auditSlot.captured.subjectId shouldBe saved.id.value
        }

        test("un entrenador también puede dar de alta alumnos (delegación, ADR-0003 D3)") {
            val userSlot = slot<User>()
            every { userRepository.save(capture(userSlot)) } returns Unit

            useCase.execute(coach, "Marta", "marta@club.local").shouldBeRight()

            userSlot.captured.role shouldBe Role.ALUMNO
            verify { invitationRepository.save(any()) }
            verify { eventPublisher.publishEvent(any<AlumnoInvitado>()) }
        }

        test("email duplicado en el club devuelve Conflict y no crea nada") {
            every { userRepository.findByEmail(club, Email.of("dup@club.local")) } returns
                User(
                    id = UserId.new(),
                    clubId = club,
                    email = Email.of("dup@club.local"),
                    name = "Ya existe",
                    role = Role.ALUMNO,
                    passwordHash = null,
                    status = UserStatus.INVITADO,
                )

            useCase
                .execute(admin, "Marta", "dup@club.local")
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { invitationRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }

        test("un ALUMNO no puede invitar: devuelve Forbidden y no produce efectos") {
            val student = Principal(userId = UUID.randomUUID(), clubId = club, role = Role.ALUMNO)

            useCase
                .execute(student, "Marta", "marta@club.local")
                .shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }

        test("nombre en blanco devuelve InvalidInput sobre el campo name") {
            val error =
                useCase
                    .execute(admin, "   ", "marta@club.local")
                    .shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.InvalidInput>()
            error.field shouldBe "name"

            verify(exactly = 0) { userRepository.save(any()) }
        }
    })
