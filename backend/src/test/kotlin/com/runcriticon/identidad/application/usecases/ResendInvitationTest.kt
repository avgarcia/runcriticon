package com.runcriticon.identidad.application.usecases
import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.application.InvitationIssuer
import com.runcriticon.identidad.application.usecases.invitation.ResendInvitationCommand
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
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

/**
 * La orquestación (rotación de token, email, auditoría) y el check de rol simétrico entrenador↔alumno
 * (LAL-62) se prueban una sola vez en [com.runcriticon.identidad.application.InvitationIssuerTest].
 * Este cascarón solo prueba lo que le es propio: el check de matriz con `Resource.COACH` y que delega
 * con `expectedRole = ENTRENADOR`.
 */
class ResendInvitationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val coachId = UserId.new()

        val invitationIssuer = mockk<InvitationIssuer>()
        val useCase = ResendInvitationCommand(invitationIssuer)

        val reissuedCoach =
            User(
                id = coachId,
                clubId = club,
                email = Email.of("coach@club.local"),
                name = "Carlos",
                role = Role.ENTRENADOR,
                passwordHash = null,
                status = UserStatus.INVITADO,
            )

        beforeTest {
            clearMocks(invitationIssuer)
            every { invitationIssuer.reissueFor(any(), any(), any()) } returns reissuedCoach.right()
        }

        test("admin reenvía: delega en InvitationIssuer.reissueFor con expectedRole ENTRENADOR") {
            useCase.execute(admin, coachId).shouldBeRight()

            verify { invitationIssuer.reissueFor(admin, coachId, Role.ENTRENADOR) }
        }

        test("propaga el Left de InvitationIssuer tal cual (incluido NotFound por id de alumno, LAL-62)") {
            every { invitationIssuer.reissueFor(any(), any(), any()) } returns IdentidadError.NotFound.left()

            useCase.execute(admin, coachId).shouldBeLeft(IdentidadError.NotFound)
        }

        test("actor con rol ENTRENADOR devuelve Forbidden y no llega a InvitationIssuer") {
            val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

            useCase.execute(coach, coachId).shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { invitationIssuer.reissueFor(any(), any(), any()) }
        }
    })
