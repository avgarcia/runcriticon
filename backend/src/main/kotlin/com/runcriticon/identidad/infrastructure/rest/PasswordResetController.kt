package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.password.ConsumePasswordResetCommand
import com.runcriticon.identidad.application.usecases.password.RequestPasswordResetCommand
import com.runcriticon.identidad.infrastructure.ratelimit.ClientIpResolver
import com.runcriticon.identidad.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.spring.SecuritySessionManager
import com.runcriticon.shared.tenancy.ClubId
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
 * Endpoints de reseteo de contraseña. En MVP mono-club el `clubId` es fijo (config); al pasar a multi-club se inferirá
 * del subdominio. Ambos son anónimos (`@NoAuthRequired`): la solicitud responde **202 neutro** siempre (no revela si la
 * cuenta existe); el consumo fija la contraseña nueva, invalida las sesiones activas y crea sesión (auto-login).
 * El handler NO toca el contexto de seguridad: delega en [SecuritySessionManager]. Espejo de [MagicLinkController].
 */
@RestController
@RequestMapping("/api/sesion/reseteo")
class PasswordResetController(
    private val requestPasswordReset: RequestPasswordResetCommand,
    private val consumePasswordReset: ConsumePasswordResetCommand,
    private val sessionManager: SecuritySessionManager,
    private val clientIpResolver: ClientIpResolver,
    @Value("\${runcriticon.bootstrap.club-id:00000000-0000-0000-0000-000000000001}")
    private val clubId: String,
) {
    @PostMapping
    @NoAuthRequired("Solicitud de reseteo: entrada anónima con respuesta neutra")
    fun request(
        @RequestBody req: PasswordResetRequest,
        request: HttpServletRequest,
    ): ResponseEntity<*> =
        requestPasswordReset
            .execute(ClubId.of(UUID.fromString(clubId)), req.email, clientIpResolver.resolve(request))
            .fold(
                { error -> error.toErrorResponse() },
                { ResponseEntity.accepted().build<Any>() },
            )

    @PostMapping("/consumo")
    @NoAuthRequired("Consumo de reseteo: el usuario se autentica con el token del email")
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
        role = SessionResponse.Role.forValue(role.code),
    )
