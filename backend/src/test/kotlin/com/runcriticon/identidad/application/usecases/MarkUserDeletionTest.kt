package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.IdentidadAccessAuditor
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.usecases.account.MarkUserDeletionCommand
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
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID

class MarkUserDeletionTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

        val userRepository = mockk<UserRepository>(relaxed = true)
        val sessionRevoker = mockk<SessionRevoker>(relaxed = true)
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val auditor = mockk<IdentidadAccessAuditor>(relaxed = true)
        val useCase = MarkUserDeletionCommand(userRepository, sessionRevoker, auditTrail, auditor)
        val ableToEnter = setOf(UserStatus.INVITADO, UserStatus.ACTIVO)

        fun user(
            role: Role,
            status: UserStatus = UserStatus.ACTIVO,
        ) = User(
            id = UserId.new(),
            clubId = club,
            email = Email.of("ana@club.local"),
            name = "Ana",
            role = role,
            passwordHash = "hash",
            status = status,
        )

        fun assertNoEffects() {
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }

        beforeTest { clearMocks(userRepository, sessionRevoker, auditTrail) }

        test("admin marca la baja: pasa a BAJA con su estado previo, revoca sesiones y audita BAJA_MARCADA") {
            val target = user(Role.ALUMNO, status = UserStatus.DESACTIVADO)
            every { userRepository.findByIdForUpdate(club, target.id) } returns target
            val saved = slot<User>()
            val audit = slot<AuditEntry>()
            every { userRepository.save(capture(saved)) } returns Unit
            every { auditTrail.record(capture(audit)) } returns Unit

            useCase.execute(admin, target.id).shouldBeRight()

            saved.captured.status shouldBe UserStatus.BAJA
            saved.captured.statusBeforeDeletion shouldBe UserStatus.DESACTIVADO
            saved.captured.markedForDeletionAt shouldNotBe null
            verify { sessionRevoker.revokeAll(target.id.value) }
            audit.captured.type shouldBe AuditEventType.BAJA_MARCADA
            // Quién pidió la baja queda aquí: la supresión al vencer la gracia ocurre sin actor (ADR-0017 D9).
            audit.captured.actorId shouldBe UserId.of(admin.userId)
            audit.captured.subjectId shouldBe target.id
        }

        test("marcar la baja no suprime nada ni publica eventos: la cuenta sigue existiendo") {
            val target = user(Role.ALUMNO)
            every { userRepository.findByIdForUpdate(club, target.id) } returns target

            useCase.execute(admin, target.id).shouldBeRight()

            verify(exactly = 0) { userRepository.deleteById(any(), any()) }
        }

        test("actor sin rol ADMIN devuelve Forbidden y no produce efectos") {
            val target = user(Role.ALUMNO)
            val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

            useCase.execute(coach, target.id).shouldBeLeft(IdentidadError.Forbidden)

            assertNoEffects()
        }

        test("el admin no puede darse de baja a si mismo") {
            useCase
                .execute(admin, UserId.of(admin.userId))
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.findByIdForUpdate(any(), any()) }
            assertNoEffects()
        }

        test("un usuario de otro club o inexistente devuelve NotFound") {
            val target = user(Role.ALUMNO)
            every { userRepository.findByIdForUpdate(club, target.id) } returns null

            useCase.execute(admin, target.id).shouldBeLeft(IdentidadError.NotFound)

            assertNoEffects()
        }

        test("una cuenta que ya esta en baja devuelve Conflict sin reiniciar la gracia") {
            val target = user(Role.ALUMNO).markForDeletion(Instant.parse("2026-10-20T10:00:00Z"))
            every { userRepository.findByIdForUpdate(club, target.id) } returns target

            useCase.execute(admin, target.id).shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()

            assertNoEffects()
        }

        test("no se puede dar de baja al ultimo admin capaz de entrar al club") {
            val target = user(Role.ADMIN)
            every { userRepository.findByIdForUpdate(club, target.id) } returns target
            every { userRepository.countByRoleAndStatuses(club, Role.ADMIN, ableToEnter) } returns 1

            useCase.execute(admin, target.id).shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()

            assertNoEffects()
        }

        test("se puede dar de baja a otro admin si queda otro capaz de entrar") {
            val target = user(Role.ADMIN)
            every { userRepository.findByIdForUpdate(club, target.id) } returns target
            every { userRepository.countByRoleAndStatuses(club, Role.ADMIN, ableToEnter) } returns 2

            useCase.execute(admin, target.id).shouldBeRight()
        }
    })
