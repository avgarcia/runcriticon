package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.IdentidadAccessAuditor
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.usecases.account.CancelUserDeletionCommand
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
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
import java.time.Duration
import java.time.Instant
import java.util.UUID

class CancelUserDeletionTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

        val userRepository = mockk<UserRepository>(relaxed = true)
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val auditor = mockk<IdentidadAccessAuditor>(relaxed = true)
        val useCase = CancelUserDeletionCommand(userRepository, auditTrail, auditor)

        fun user(status: UserStatus = UserStatus.ACTIVO) =
            User(
                id = UserId.new(),
                clubId = club,
                email = Email.of("ana@club.local"),
                name = "Ana",
                role = Role.ENTRENADOR,
                passwordHash = "hash",
                status = status,
            )

        fun markedDaysAgo(days: Long) = user().markForDeletion(Instant.now().minus(Duration.ofDays(days)))

        fun assertNoEffects() {
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }

        beforeTest { clearMocks(userRepository, auditTrail) }

        test("admin cancela la baja durante la gracia: vuelve al estado previo y audita BAJA_CANCELADA") {
            val target = markedDaysAgo(29)
            every { userRepository.findByIdForUpdate(club, target.id) } returns target
            val saved = slot<User>()
            val audit = slot<AuditEntry>()
            every { userRepository.save(capture(saved)) } returns Unit
            every { auditTrail.record(capture(audit)) } returns Unit

            useCase.execute(admin, target.id).shouldBeRight()

            saved.captured.status shouldBe UserStatus.ACTIVO
            saved.captured.markedForDeletionAt shouldBe null
            saved.captured.statusBeforeDeletion shouldBe null
            audit.captured.type shouldBe AuditEventType.BAJA_CANCELADA
            audit.captured.actorId shouldBe UserId.of(admin.userId)
            audit.captured.subjectId shouldBe target.id
        }

        test("actor sin rol ADMIN devuelve Forbidden y no produce efectos") {
            val target = markedDaysAgo(1)
            val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

            useCase.execute(coach, target.id).shouldBeLeft(IdentidadError.Forbidden)

            assertNoEffects()
        }

        test("una cuenta de otro club, inexistente o ya suprimida devuelve NotFound") {
            val target = markedDaysAgo(1)
            every { userRepository.findByIdForUpdate(club, target.id) } returns null

            useCase.execute(admin, target.id).shouldBeLeft(IdentidadError.NotFound)

            assertNoEffects()
        }

        test("una cuenta que no esta en baja devuelve Conflict") {
            val target = user()
            every { userRepository.findByIdForUpdate(club, target.id) } returns target

            useCase.execute(admin, target.id).shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()

            assertNoEffects()
        }

        test("una baja con la gracia vencida ya no se cancela: esta pendiente de suprimirse") {
            val target = markedDaysAgo(31)
            every { userRepository.findByIdForUpdate(club, target.id) } returns target

            useCase.execute(admin, target.id).shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()

            assertNoEffects()
        }
    })
