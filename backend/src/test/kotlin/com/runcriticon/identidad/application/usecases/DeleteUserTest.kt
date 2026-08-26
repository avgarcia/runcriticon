package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.api.events.AdminEliminado
import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.MagicLinkRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.PasswordHistory
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.usecases.account.DeleteUserCommand
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
import com.runcriticon.shared.events.IntegrationEvent
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
import io.mockk.verifyOrder
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class DeleteUserTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

        val userRepository = mockk<UserRepository>(relaxed = true)
        val invitationRepository = mockk<InvitationRepository>(relaxed = true)
        val magicLinkRepository = mockk<MagicLinkRepository>(relaxed = true)
        val passwordHistory = mockk<PasswordHistory>(relaxed = true)
        val consentRepository = mockk<ConsentRepository>(relaxed = true)
        val sessionRevoker = mockk<SessionRevoker>(relaxed = true)
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val useCase =
            DeleteUserCommand(
                userRepository,
                invitationRepository,
                magicLinkRepository,
                passwordHistory,
                consentRepository,
                sessionRevoker,
                auditTrail,
                eventPublisher,
            )

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

        beforeTest {
            clearMocks(
                userRepository,
                invitationRepository,
                magicLinkRepository,
                passwordHistory,
                consentRepository,
                sessionRevoker,
                auditTrail,
                eventPublisher,
            )
        }

        test("admin elimina a un alumno: borra sus datos, revoca sesiones, audita y publica la baja") {
            val target = user(Role.ALUMNO)
            every { userRepository.findById(club, target.id) } returns target
            val auditSlot = slot<AuditEntry>()
            val eventSlot = slot<Any>()
            every { auditTrail.record(capture(auditSlot)) } returns Unit
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

            useCase.execute(admin, target.id).shouldBeRight()

            verify { sessionRevoker.revokeAll(target.id.value) }
            auditSlot.captured.type shouldBe AuditEventType.CUENTA_ELIMINADA
            auditSlot.captured.actorId shouldBe admin.userId
            // El asiento no lleva el id del sujeto suprimido: el enlace solicitud↔persona vive en el runbook.
            auditSlot.captured.subjectId shouldBe null

            val event = eventSlot.captured.shouldBeInstanceOf<AlumnoEliminado>()
            event.aggregateId shouldBe target.id.value
            event.clubId shouldBe club.value
            // El actor es quien ejecuta la supresión, no el sujeto suprimido.
            event.actorId shouldBe admin.userId
        }

        test("la auditoria previa del sujeto se anonimiza antes de escribir el asiento de la baja") {
            val target = user(Role.ALUMNO)
            every { userRepository.findById(club, target.id) } returns target

            useCase.execute(admin, target.id).shouldBeRight()

            verify { auditTrail.anonymize(target.id.value, target.email) }
            verifyOrder {
                auditTrail.anonymize(target.id.value, target.email)
                auditTrail.record(any())
            }
        }

        test("las filas dependientes se borran antes que el usuario, porque sus claves ajenas no cascadean") {
            val target = user(Role.ALUMNO)
            every { userRepository.findById(club, target.id) } returns target

            useCase.execute(admin, target.id).shouldBeRight()

            verifyOrder {
                magicLinkRepository.deleteByUserId(club, target.id)
                invitationRepository.deleteByUserId(club, target.id)
                passwordHistory.deleteByUserId(club, target.id)
                consentRepository.deleteByUserId(club, target.id)
                userRepository.deleteById(club, target.id)
            }
        }

        test("eliminar a un entrenador publica su propia baja") {
            val target = user(Role.ENTRENADOR)
            every { userRepository.findById(club, target.id) } returns target
            val eventSlot = slot<Any>()
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

            useCase.execute(admin, target.id).shouldBeRight()

            eventSlot.captured.shouldBeInstanceOf<EntrenadorEliminado>().aggregateId shouldBe target.id.value
        }

        test("eliminar a otro admin publica su propia baja (LAL-126)") {
            val target = user(Role.ADMIN)
            every { userRepository.findById(club, target.id) } returns target
            every { userRepository.countByRoleExcludingStatus(club, Role.ADMIN, UserStatus.DESACTIVADO) } returns 2
            val eventSlot = slot<Any>()
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

            useCase.execute(admin, target.id).shouldBeRight()

            verify { userRepository.deleteById(club, target.id) }
            eventSlot.captured.shouldBeInstanceOf<AdminEliminado>().aggregateId shouldBe target.id.value
        }

        test("se puede eliminar una cuenta que nunca llego a activarse") {
            val target = user(Role.ALUMNO, status = UserStatus.INVITADO)
            every { userRepository.findById(club, target.id) } returns target

            useCase.execute(admin, target.id).shouldBeRight()

            verify { userRepository.deleteById(club, target.id) }
        }

        test("se puede eliminar una cuenta ya desactivada") {
            val target = user(Role.ALUMNO, status = UserStatus.DESACTIVADO)
            every { userRepository.findById(club, target.id) } returns target

            useCase.execute(admin, target.id).shouldBeRight()

            verify { userRepository.deleteById(club, target.id) }
        }

        test("actor sin rol ADMIN devuelve Forbidden y no produce efectos") {
            val target = user(Role.ALUMNO)
            val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

            useCase.execute(coach, target.id).shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { userRepository.deleteById(any(), any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any<IntegrationEvent>()) }
        }

        test("un usuario de otro club o inexistente devuelve NotFound") {
            val target = user(Role.ALUMNO)
            every { userRepository.findById(club, target.id) } returns null

            useCase.execute(admin, target.id).shouldBeLeft(IdentidadError.NotFound)

            verify(exactly = 0) { userRepository.deleteById(any(), any()) }
        }

        test("el admin no puede eliminarse a si mismo") {
            val result = useCase.execute(admin, UserId.of(admin.userId))

            result.shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()
            verify(exactly = 0) { userRepository.deleteById(any(), any()) }
            // Ni siquiera se consulta el repositorio: la comprobación es sobre el propio principal.
            verify(exactly = 0) { userRepository.findById(any(), any()) }
        }

        test("no se puede eliminar al ultimo admin capaz de entrar al club") {
            val target = user(Role.ADMIN)
            every { userRepository.findById(club, target.id) } returns target
            every { userRepository.countByRoleExcludingStatus(club, Role.ADMIN, UserStatus.DESACTIVADO) } returns 1

            useCase.execute(admin, target.id).shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.deleteById(any(), any()) }
        }

        test("un admin desactivado no cuenta como administrador que sostenga el club") {
            val target = user(Role.ADMIN)
            every { userRepository.findById(club, target.id) } returns target
            every { userRepository.countByRoleExcludingStatus(club, Role.ADMIN, UserStatus.DESACTIVADO) } returns 1

            useCase.execute(admin, target.id).shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()

            // El conteo excluye DESACTIVADO a propósito: un admin desactivado no puede iniciar sesión, así que
            // dejar el club solo con él lo dejaría huérfano.
            verify { userRepository.countByRoleExcludingStatus(club, Role.ADMIN, UserStatus.DESACTIVADO) }
        }

        test("eliminar a un alumno no consulta la regla del ultimo admin") {
            val target = user(Role.ALUMNO)
            every { userRepository.findById(club, target.id) } returns target

            useCase.execute(admin, target.id).shouldBeRight()

            verify(exactly = 0) { userRepository.countByRoleExcludingStatus(any(), any(), any()) }
        }
    })
