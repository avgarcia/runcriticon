package com.runcriticon.identidad.infrastructure.rest

import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.application.usecases.InviteCoach
import com.runcriticon.identidad.application.usecases.ListCoaches
import com.runcriticon.identidad.application.usecases.ResendInvitation
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * Test unitario de [CoachController]: verifica el mapeo `Either`→`ResponseEntity` sin contexto
 * Spring. La autenticación real, el CSRF y el enrutamiento de Spring MVC se cubren en integración
 * con Testcontainers (LAL-50).
 */
class CoachControllerTest :
    FunSpec({
        val inviteCoach = mockk<InviteCoach>()
        val listCoaches = mockk<ListCoaches>()
        val resendInvitation = mockk<ResendInvitation>()
        val principalProvider = mockk<PrincipalProvider>()
        val controller = CoachController(inviteCoach, listCoaches, resendInvitation, principalProvider)

        val adminPrincipal =
            Principal(
                userId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                clubId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                role = Role.ADMIN,
            )
        val coachId = UUID.fromString("11111111-1111-1111-1111-111111111111")

        beforeEach {
            every { principalProvider.current() } returns adminPrincipal
        }

        // --- POST /api/entrenadores ---

        test("invite - 201 cuando el caso de uso devuelve Right") {
            every { inviteCoach.execute(any(), any(), any()) } returns UserId.of(coachId).right()

            val resp = controller.invite(InviteCoachRequest(nombre = "Ana García", email = "ana@club.es"))

            resp.statusCode shouldBe HttpStatus.CREATED
            (resp.body as InviteCoachResponse).id shouldBe coachId
        }

        test("invite - 409 cuando el caso de uso devuelve Left(Conflict)") {
            every { inviteCoach.execute(any(), any(), any()) } returns
                IdentidadError.Conflict("ya existe un usuario con ese email en el club").left()

            val resp = controller.invite(InviteCoachRequest(nombre = "Ana García", email = "ana@club.es"))

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "CONFLICT"
        }

        test("invite - 403 cuando el caso de uso devuelve Left(Forbidden)") {
            every { inviteCoach.execute(any(), any(), any()) } returns IdentidadError.Forbidden.left()

            val resp = controller.invite(InviteCoachRequest(nombre = "Ana García", email = "ana@club.es"))

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            (resp.body as ErrorResponse).code shouldBe "FORBIDDEN"
        }

        test("invite - 400 cuando el caso de uso devuelve Left(InvalidInput)") {
            every { inviteCoach.execute(any(), any(), any()) } returns
                IdentidadError.InvalidInput("email", "invalid").left()

            val resp = controller.invite(InviteCoachRequest(nombre = "Ana García", email = "no-es-email"))

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            (resp.body as ErrorResponse).code shouldBe "INVALID_INPUT"
            (resp.body as ErrorResponse).field shouldBe "email"
        }

        // --- POST /api/entrenadores/{id}/invitaciones ---

        test("resend - 204 cuando el caso de uso devuelve Right") {
            every { resendInvitation.execute(any(), any()) } returns Unit.right()

            val resp = controller.resend(coachId)

            resp.statusCode shouldBe HttpStatus.NO_CONTENT
        }

        test("resend - 404 cuando el caso de uso devuelve Left(NotFound)") {
            every { resendInvitation.execute(any(), any()) } returns IdentidadError.NotFound.left()

            val resp = controller.resend(coachId)

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            (resp.body as ErrorResponse).code shouldBe "NOT_FOUND"
        }
    })
