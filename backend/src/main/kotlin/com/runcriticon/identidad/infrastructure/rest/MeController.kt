package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.consent.GrantConsentCommand
import com.runcriticon.identidad.application.usecases.consent.QueryMyConsentQuery
import com.runcriticon.identidad.application.usecases.consent.RevokeConsentCommand
import com.runcriticon.identidad.application.usecases.session.QueryMyPermissionsQuery
import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.infrastructure.ratelimit.ClientIpResolver
import com.runcriticon.identidad.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.shared.api.rest.MiConsentimientoRequest
import com.runcriticon.shared.api.rest.MiConsentimientoResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.AuthenticatedOnly
import com.runcriticon.shared.autorizacion.annotations.Authorize
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneOffset

/** Endpoints sobre el propio usuario autenticado, fuera de la gestión de sesión (`SessionController`). */
@RestController
@RequestMapping("/api/me")
class MeController(
    private val queryMyPermissions: QueryMyPermissionsQuery,
    private val queryMyConsent: QueryMyConsentQuery,
    private val grantConsent: GrantConsentCommand,
    private val revokeConsent: RevokeConsentCommand,
    private val principalProvider: PrincipalProvider,
    private val clientIpResolver: ClientIpResolver,
) {
    /**
     * `GET /me/permissions`: ayuda de UX para que el frontend oculte botones a los que el usuario no llegaría. Nunca es
     * una barrera — cada petición real se autoriza en el servidor con independencia de lo que este endpoint devuelva.
     */
    @GetMapping("/permissions")
    @AuthenticatedOnly(
        "Devuelve los propios permisos del rol autenticado; no hay recurso de terceros que autorizar",
    )
    fun permissions(): Map<Resource, Set<Action>> = queryMyPermissions.execute()

    /** `GET /me/consentimiento` — el propio estado de consentimiento de datos de salud (LAL-128). */
    @GetMapping("/consentimiento")
    @AuthenticatedOnly(
        "Devuelve el propio estado de consentimiento del alumno; no hay recurso de terceros que autorizar",
    )
    fun consent(): MiConsentimientoResponse = queryMyConsent.execute(principalProvider.current()).toResponse()

    /** `POST /me/consentimiento` — conceder, o volver a conceder tras revocar. */
    @PostMapping("/consentimiento")
    @Authorize("CONSENT:GRANT")
    fun grant(
        @RequestBody req: MiConsentimientoRequest,
        request: HttpServletRequest,
    ): ResponseEntity<*> =
        grantConsent
            .execute(
                actor = principalProvider.current(),
                consentVersion = req.versionConsentimiento,
                clientIp = clientIpResolver.resolve(request),
                userAgent = request.getHeader("User-Agent") ?: "unknown",
            ).fold(
                { error -> error.toErrorResponse() },
                { consent -> ResponseEntity.ok(consent.toResponse()) },
            )

    /** `DELETE /me/consentimiento` — revocar. */
    @DeleteMapping("/consentimiento")
    @Authorize("CONSENT:REVOKE")
    fun revoke(): ResponseEntity<*> =
        revokeConsent.execute(principalProvider.current()).fold(
            { error -> error.toErrorResponse() },
            { consent -> ResponseEntity.ok(consent.toResponse()) },
        )
}

/** `null` (nunca ha concedido) se traduce a `PENDIENTE`, el único caso sin fila de consentimiento. */
private fun Consent?.toResponse(): MiConsentimientoResponse =
    when {
        this == null ->
            MiConsentimientoResponse(estado = MiConsentimientoResponse.Estado.PENDIENTE)

        isActive() ->
            MiConsentimientoResponse(
                estado = MiConsentimientoResponse.Estado.VIGENTE,
                versionTexto = textVersion,
                concedidoEn = grantedAt.atOffset(ZoneOffset.UTC),
            )

        else ->
            MiConsentimientoResponse(
                estado = MiConsentimientoResponse.Estado.REVOCADO,
                versionTexto = textVersion,
                concedidoEn = grantedAt.atOffset(ZoneOffset.UTC),
                revocadoEn = revokedAt?.atOffset(ZoneOffset.UTC),
            )
    }
