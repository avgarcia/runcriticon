package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.ConsumePasswordReset
import com.runcriticon.identidad.application.usecases.RequestPasswordReset
import com.runcriticon.identidad.infrastructure.ratelimit.ClientIpResolver
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.spring.SecuritySessionManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Endpoints de reseteo de contraseña (LAL-12, ADR-0003 D8). En MVP mono-club el `clubId` es fijo
 * (config); al pasar a multi-club se inferirá del subdominio (ADR-0006 D16). Ambos son anónimos
 * (`@NoAuthRequired`): la solicitud responde **202 neutro** siempre (no revela si la cuenta existe);
 * el consumo fija la contraseña nueva, invalida las sesiones activas (D8) y crea sesión (auto-login).
 * El handler NO toca el contexto de seguridad: delega en [SecuritySessionManager]. Espejo de
 * [MagicLinkController].
 */
@RestController
@RequestMapping("/api/sesion/reseteo")
class PasswordResetController(
    private val requestPasswordReset: RequestPasswordReset,
    private val consumePasswordReset: ConsumePasswordReset,
    private val sessionManager: SecuritySessionManager,
    private val clientIpResolver: ClientIpResolver,
    @Value("\${runcriticon.bootstrap.club-id:00000000-0000-0000-0000-000000000001}")
    private val clubId: String,
) {
    @PostMapping
    @NoAuthRequired("Solicitud de reseteo: entrada anónima con respuesta neutra (ADR-0003 D8)")
    fun request(
        @RequestBody req: PasswordResetRequest,
        request: HttpServletRequest,
    ): ResponseEntity<*> =
        requestPasswordReset.execute(UUID.fromString(clubId), req.email, clientIpResolver.resolve(request)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.accepted().build<Any>() },
        )

    @PostMapping("/consumo")
    @NoAuthRequired("Consumo de reseteo: el usuario se autentica con el token del email (ADR-0003 D8)")
    fun consume(
        @RequestBody req: PasswordResetConsumeRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> =
        consumePasswordReset.execute(req.token, req.newPassword).fold(
            { error -> error.toErrorResponse() },
            { principal ->
                sessionManager.startSession(principal, request, response)
                ResponseEntity.ok(principal.toSessionResponse())
            },
        )
}

private fun Principal.toSessionResponse(): SessionResponse =
    SessionResponse(
        userId = userId,
        clubId = clubId,
        role = role.code,
    )
