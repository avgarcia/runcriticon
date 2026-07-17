package com.runcriticon.identidad.application.usecases

import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.api.events.EntrenadorInvitado
import com.runcriticon.identidad.application.InvitationIssuer
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.events.UserInvited
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
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
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

/**
 * La orquestación (crear usuario, token, email, auditoría) se prueba una sola vez en
 * [com.runcriticon.identidad.application.InvitationIssuerTest]. Este cascarón solo prueba lo que le
 * es propio: el check de matriz con `Resource.COACH`, que delega con `role = ENTRENADOR` y que
 * publica el integration event [EntrenadorInvitado] (LAL-54).
 */
class InviteCoachTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

        val invitationIssuer = mockk<InvitationIssuer>()
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val useCase = InviteCoach(invitationIssuer, eventPublisher)

        val createdCoach = User.newInvited(club, Email.of("carlos@club.local"), "Carlos", Role.ENTRENADOR)
        val invitedEvent =
            UserInvited(
                eventId = UUID.randomUUID(),
                occurredAt = Instant.now(),
                user = createdCoach,
                actorId = admin.userId,
            )

        beforeTest {
            clearMocks(invitationIssuer, eventPublisher)
            every { invitationIssuer.issue(any(), any(), any(), any()) } returns invitedEvent.right()
        }

        test(
            "admin invita: delega con role ENTRENADOR, publica EntrenadorInvitado y devuelve su id",
        ) {
            val eventSlot = slot<Any>()
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

            val createdId = useCase.execute(admin, "Carlos", "carlos@club.local").shouldBeRight()
            createdId shouldBe createdCoach.id

            verify { invitationIssuer.issue(admin, "Carlos", "carlos@club.local", Role.ENTRENADOR) }

            val published = eventSlot.captured.shouldBeInstanceOf<EntrenadorInvitado>()
            published.aggregateId shouldBe createdCoach.id.value
            published.clubId shouldBe club.value
            published.actorId shouldBe admin.userId
            published.name shouldBe createdCoach.name
            published.email shouldBe createdCoach.email.value
        }

        test("propaga el Left de InvitationIssuer y no publica EntrenadorInvitado") {
            every { invitationIssuer.issue(any(), any(), any(), any()) } returns
                IdentidadError.Conflict("ya existe un usuario con ese email en el club").left()

            useCase
                .execute(admin, "Carlos", "dup@club.local")
                .shouldBeLeft(IdentidadError.Conflict("ya existe un usuario con ese email en el club"))

            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        test("actor sin rol ADMIN devuelve Forbidden y no llega a InvitationIssuer") {
            val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

            useCase.execute(coach, "Carlos", "carlos@club.local").shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { invitationIssuer.issue(any(), any(), any(), any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }
    })
