package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.account.ActivateAccountCommand
import com.runcriticon.identidad.application.usecases.invitation.InvitationDetails
import com.runcriticon.identidad.application.usecases.invitation.ResolveInvitationQuery
import com.runcriticon.identidad.infrastructure.ratelimit.ClientIpResolver
import com.runcriticon.identidad.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.shared.api.rest.ActivationRequest
import com.runcriticon.shared.api.rest.ActivationResponse
import com.runcriticon.shared.api.rest.DetalleInvitacion
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.spring.SecuritySessionManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoint público de activación de cuenta. Es anónimo: el invitado se autentica con el token del email, no con la
 * matriz (no hay principal). Tras activar, inicia sesión (auto-login) vía [SecuritySessionManager], igual que el login.
 * El error se mapea a 4xx estructurado.
 *
 * Resuelve la IP con [ClientIpResolver] (mismo componente que usan `SessionController`/`MagicLinkController`) y lee
 * `User-Agent` directamente: son los metadatos del consentimiento de datos de salud (ADR-0014 D18) cuando
 * el invitado es ALUMNO — `ActivateAccountCommand` los ignora para el resto de roles.
 */
@RestController
@RequestMapping("/api/activacion")
class ActivationController(
    private val activateAccount: ActivateAccountCommand,
    private val resolveInvitation: ResolveInvitationQuery,
    private val sessionManager: SecuritySessionManager,
    private val clientIpResolver: ClientIpResolver,
) {
    @PostMapping
    @NoAuthRequired("Activación pública: el invitado se autentica con el token del email")
    fun activate(
        @RequestBody req: ActivationRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> =
        activateAccount
            .execute(
                rawToken = req.token,
                password = req.password,
                consentGranted = req.consentimiento ?: false,
                consentVersion = req.versionConsentimiento,
                clientIp = clientIpResolver.resolve(request),
                userAgent = request.getHeader("User-Agent") ?: "unknown",
            ).fold(
                { error -> error.toErrorResponse() },
                { principal ->
                    sessionManager.startSession(principal, request, response)
                    ResponseEntity.ok(principal.toActivationResponse())
                },
            )

    /** Consulta previa a [activate]: la tarjeta de contexto de la pantalla de activación. */
    @GetMapping
    @NoAuthRequired("Consulta pública: el invitado aún no tiene sesión, se identifica con el token del email")
    fun resolveInvitation(
        @RequestParam token: String,
        request: HttpServletRequest,
    ): ResponseEntity<*> =
        resolveInvitation
            .execute(rawToken = token, clientIp = clientIpResolver.resolve(request))
            .fold(
                { error -> error.toErrorResponse() },
                { details -> ResponseEntity.ok(details.toDetalleInvitacion()) },
            )
}

private fun Principal.toActivationResponse(): ActivationResponse =
    ActivationResponse(
        userId = userId,
        clubId = clubId,
        role = ActivationResponse.Role.forValue(role.code),
    )

private fun InvitationDetails.toDetalleInvitacion(): DetalleInvitacion =
    DetalleInvitacion(
        nombre = name,
        club = club,
        rol = DetalleInvitacion.Rol.forValue(role.code),
        invitadoPor = invitedBy,
    )
