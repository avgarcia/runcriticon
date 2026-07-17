package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.UserRepository
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
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID

class RevokeUserSessionsTest :
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
        val useCase = RevokeUserSessions(userRepository, sessionRevoker, auditTrail)

        beforeTest {
            clearMocks(userRepository, sessionRevoker, auditTrail)
            every { userRepository.findById(club, target.id) } returns target
        }

        test("admin revoca: borra todas las sesiones del usuario y audita SESION_REVOCADA") {
            val auditSlot = slot<AuditEntry>()
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            useCase.execute(admin, target.id).shouldBeRight()

            verify { sessionRevoker.revokeAll(target.id.value) }
            auditSlot.captured.type shouldBe AuditEventType.SESION_REVOCADA
            auditSlot.captured.actorId shouldBe admin.userId
            auditSlot.captured.subjectId shouldBe target.id.value
        }

        test("actor sin rol ADMIN devuelve Forbidden y no revoca ni audita") {
            val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

            useCase.execute(coach, target.id).shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }

        test("usuario de otro club (findById null) devuelve NotFound y no revoca") {
            every { userRepository.findById(club, target.id) } returns null

            useCase.execute(admin, target.id).shouldBeLeft(IdentidadError.NotFound)

            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }
    })
