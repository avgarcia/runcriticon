package com.runcriticon.identidad.application.usecases

import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.api.events.AlumnoInvitado
import com.runcriticon.identidad.application.InvitationIssuer
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.shared.autorizacion.model.ClubId
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

/**
 * La orquestación (crear usuario, token, email, auditoría) se prueba una sola vez en
 * [com.runcriticon.identidad.application.InvitationIssuerTest]. Este cascarón solo prueba lo que le
 * es propio: el check de matriz con `Resource.STUDENT`, que delega con `role = ALUMNO` y que publica
 * el integration event [AlumnoInvitado].
 */
class InviteStudentTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

        val invitationIssuer = mockk<InvitationIssuer>()
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val useCase = InviteStudent(invitationIssuer, eventPublisher)

        val createdStudent = User.newInvited(club, Email.of("marta@club.local"), "Marta", Role.ALUMNO)

        beforeTest {
            clearMocks(invitationIssuer, eventPublisher)
            every { invitationIssuer.issue(any(), any(), any(), any()) } returns createdStudent.right()
        }

        test(
            "admin invita: delega en InvitationIssuer.issue con role ALUMNO, publica AlumnoInvitado y devuelve su id",
        ) {
            val eventSlot = slot<Any>()
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

            val createdId = useCase.execute(admin, "Marta", "marta@club.local").shouldBeRight()
            createdId shouldBe createdStudent.id

            verify { invitationIssuer.issue(admin, "Marta", "marta@club.local", Role.ALUMNO) }

            val published = eventSlot.captured.shouldBeInstanceOf<AlumnoInvitado>()
            published.aggregateId shouldBe createdStudent.id.value
            published.clubId shouldBe club.value
            published.actorId shouldBe admin.userId
            published.name shouldBe createdStudent.name
            published.email shouldBe createdStudent.email.value
        }

        test("un entrenador también puede dar de alta alumnos (delegación, ADR-0003 D3)") {
            useCase.execute(coach, "Marta", "marta@club.local").shouldBeRight()

            verify { invitationIssuer.issue(coach, "Marta", "marta@club.local", Role.ALUMNO) }
            verify { eventPublisher.publishEvent(any<AlumnoInvitado>()) }
        }

        test("propaga el Left de InvitationIssuer y no publica AlumnoInvitado") {
            every { invitationIssuer.issue(any(), any(), any(), any()) } returns
                IdentidadError.Conflict("ya existe un usuario con ese email en el club").left()

            useCase
                .execute(admin, "Marta", "dup@club.local")
                .shouldBeLeft(IdentidadError.Conflict("ya existe un usuario con ese email en el club"))

            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        test("un ALUMNO no puede invitar: devuelve Forbidden y no llega a InvitationIssuer") {
            val student = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)

            useCase.execute(student, "Marta", "marta@club.local").shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { invitationIssuer.issue(any(), any(), any(), any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }
    })
