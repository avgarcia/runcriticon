package com.runcriticon.identidad.application.usecases
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.usecases.account.DeactivateUserCommand
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.SessionRevoker
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
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
import java.util.UUID

class DeactivateUserTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val target =
            User(
                id = UserId.new(),
                clubId = club,
                email = Email.of("ana@club.local"),
                name = "Ana",
                role = Role.ENTRENADOR,
                passwordHash = "hash",
                status = UserStatus.ACTIVO,
            )

        val userRepository = mockk<UserRepository>(relaxed = true)
        val sessionRevoker = mockk<SessionRevoker>(relaxed = true)
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val useCase = DeactivateUserCommand(userRepository, sessionRevoker, auditTrail)

        beforeTest {
            clearMocks(userRepository, sessionRevoker, auditTrail)
            every { userRepository.findById(club, target.id) } returns target
        }

        test("admin desactiva: pasa a DESACTIVADO, revoca sesiones y audita CUENTA_DESACTIVADA") {
            val savedSlot = slot<User>()
            val auditSlot = slot<AuditEntry>()
            every { userRepository.save(capture(savedSlot)) } returns Unit
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            useCase.execute(admin, target.id).shouldBeRight()

            savedSlot.captured.status shouldBe UserStatus.DESACTIVADO
            verify { sessionRevoker.revokeAll(target.id.value) }
            auditSlot.captured.type shouldBe AuditEventType.CUENTA_DESACTIVADA
            auditSlot.captured.actorId shouldBe admin.userId
            auditSlot.captured.subjectId shouldBe target.id.value
        }

        test("actor sin rol ADMIN devuelve Forbidden y no produce efectos") {
            val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

            useCase.execute(coach, target.id).shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }

        test("usuario de otro club (findById null) devuelve NotFound") {
            every { userRepository.findById(club, target.id) } returns null

            useCase.execute(admin, target.id).shouldBeLeft(IdentidadError.NotFound)

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
        }

        test("cuenta ya DESACTIVADO devuelve Conflict y no revoca ni guarda") {
            val yaInactivo =
                User(
                    id = UserId.new(),
                    clubId = club,
                    email = Email.of("bea@club.local"),
                    name = "Bea",
                    role = Role.ENTRENADOR,
                    passwordHash = "hash",
                    status = UserStatus.DESACTIVADO,
                )
            every { userRepository.findById(club, yaInactivo.id) } returns yaInactivo

            useCase
                .execute(admin, yaInactivo.id)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }
    })
