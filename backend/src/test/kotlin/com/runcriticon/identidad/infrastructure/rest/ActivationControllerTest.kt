package com.runcriticon.identidad.infrastructure.rest

import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.application.usecases.ActivateAccount
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.autorizacion.spring.SecuritySessionManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * Test unitario de [ActivationController]: verifica el mapeo `Either`→`ResponseEntity` y el auto-login
 * sin contexto Spring. El flujo real (CSRF, enrutado, sesión persistida) se cubre en integración.
 */
class ActivationControllerTest :
    FunSpec({
        val activateAccount = mockk<ActivateAccount>()
        val sessionManager = mockk<SecuritySessionManager>(relaxed = true)
        val controller = ActivationController(activateAccount, sessionManager)
        val request = mockk<HttpServletRequest>(relaxed = true)
        val response = mockk<HttpServletResponse>(relaxed = true)

        val principal =
            Principal(
                userId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                clubId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                role = Role.ALUMNO,
            )

        test("200 con ActivationResponse e inicia sesión cuando el caso de uso devuelve Right") {
            every { activateAccount.execute(any(), any()) } returns principal.right()

            val resp =
                controller.activate(
                    ActivationRequest(token = "tok", password = "clave-clave-clave"),
                    request,
                    response,
                )

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as ActivationResponse).userId shouldBe principal.userId
            verify { sessionManager.startSession(principal, request, response) }
        }

        test("400 cuando InvalidInput y NO inicia sesión") {
            every { activateAccount.execute(any(), any()) } returns
                IdentidadError.InvalidInput("token", "mismatch").left()

            val resp = controller.activate(ActivationRequest(token = "tok", password = "x"), request, response)

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            (resp.body as ErrorResponse).code shouldBe "INVALID_INPUT"
            verify(exactly = 0) { sessionManager.startSession(any(), any(), any()) }
        }

        test("409 cuando Conflict") {
            every { activateAccount.execute(any(), any()) } returns
                IdentidadError.Conflict("la cuenta ya está activa").left()

            val resp = controller.activate(ActivationRequest(token = "tok", password = "x"), request, response)

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "CONFLICT"
        }
    })
