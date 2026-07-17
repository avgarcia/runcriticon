package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.ConsumeMagicLink
import com.runcriticon.identidad.application.usecases.RequestMagicLink
import com.runcriticon.identidad.infrastructure.ratelimit.ClientIpResolver
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
 * Endpoints de magic link (ADR-0003 D5). En MVP mono-club el `clubId` es fijo (config); al pasar a
 * multi-club se inferirá del subdominio (ADR-0006 D16). Ambos son anónimos (`@NoAuthRequired`): la
 * petición responde **202 neutro** siempre (no revela si la cuenta existe); el consumo crea sesión.
 * El handler NO toca el contexto de seguridad: delega en [SecuritySessionManager].
 */
@RestController
@RequestMapping("/api/sesion/magic-link")
class MagicLinkController(
    private val requestMagicLink: RequestMagicLink,
    private val consumeMagicLink: ConsumeMagicLink,
    private val sessionManager: SecuritySessionManager,
    private val clientIpResolver: ClientIpResolver,
    @Value("\${runcriticon.bootstrap.club-id:00000000-0000-0000-0000-000000000001}")
    private val clubId: String,
) {
    @PostMapping
    @NoAuthRequired("Solicitud de magic link: entrada anónima con respuesta neutra (ADR-0003 D5)")
    fun request(
        @RequestBody req: MagicLinkRequest,
        request: HttpServletRequest,
    ): ResponseEntity<*> =
        requestMagicLink
            .execute(ClubId.of(UUID.fromString(clubId)), req.email, clientIpResolver.resolve(request))
            .fold(
                { error -> error.toErrorResponse() },
                { ResponseEntity.accepted().build<Any>() },
            )

    @PostMapping("/consumo")
    @NoAuthRequired("Consumo de magic link: el usuario se autentica con el token del email (ADR-0003 D5)")
    fun consume(
        @RequestBody req: MagicLinkConsumeRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> =
        consumeMagicLink.execute(req.token).fold(
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
