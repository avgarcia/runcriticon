package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.ActivateAccount
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.spring.SecuritySessionManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoint público de activación de cuenta (LAL-9, ADR-0003 D4/D6). Es anónimo: el invitado se
 * autentica con el token del email, no con la matriz (no hay principal). Tras activar, inicia sesión
 * (auto-login) vía [SecuritySessionManager], igual que el login. El error se mapea a 4xx estructurado.
 */
@RestController
@RequestMapping("/api/activacion")
class ActivationController(
    private val activateAccount: ActivateAccount,
    private val sessionManager: SecuritySessionManager,
) {
    @PostMapping
    @NoAuthRequired("Activación pública: el invitado se autentica con el token del email (ADR-0003 D4)")
    fun activate(
        @RequestBody req: ActivationRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> =
        activateAccount.execute(req.token, req.password).fold(
            { error -> error.toErrorResponse() },
            { principal ->
                sessionManager.startSession(principal, request, response)
                ResponseEntity.ok(principal.toActivationResponse())
            },
        )
}

private fun Principal.toActivationResponse(): ActivationResponse =
    ActivationResponse(
        userId = userId,
        clubId = clubId,
        role = role.code,
    )
