package com.runcriticon.identidad.application.usecases

import arrow.core.left
import arrow.core.right
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
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

/**
 * La orquestación (crear usuario, token, email, auditoría) se prueba una sola vez en
 * [com.runcriticon.identidad.application.InvitationIssuerTest]. Este cascarón solo prueba lo que le
 * es propio: el check de matriz con `Resource.COACH` y que delega con `role = ENTRENADOR`.
 */
class InviteCoachTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

        val invitationIssuer = mockk<InvitationIssuer>()
        val useCase = InviteCoach(invitationIssuer)

        val createdCoach = User.newInvited(club, Email.of("carlos@club.local"), "Carlos", Role.ENTRENADOR)

        beforeTest {
            clearMocks(invitationIssuer)
            every { invitationIssuer.issue(any(), any(), any(), any()) } returns createdCoach.right()
        }

        test("admin invita: delega en InvitationIssuer.issue con role ENTRENADOR y devuelve su id") {
            val createdId = useCase.execute(admin, "Carlos", "carlos@club.local").shouldBeRight()
            createdId shouldBe createdCoach.id

            verify { invitationIssuer.issue(admin, "Carlos", "carlos@club.local", Role.ENTRENADOR) }
        }

        test("propaga el Left de InvitationIssuer tal cual") {
            every { invitationIssuer.issue(any(), any(), any(), any()) } returns
                IdentidadError.Conflict("ya existe un usuario con ese email en el club").left()

            useCase
                .execute(admin, "Carlos", "dup@club.local")
                .shouldBeLeft(IdentidadError.Conflict("ya existe un usuario con ese email en el club"))
        }

        test("actor sin rol ADMIN devuelve Forbidden y no llega a InvitationIssuer") {
            val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

            useCase.execute(coach, "Carlos", "carlos@club.local").shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { invitationIssuer.issue(any(), any(), any(), any()) }
        }
    })
