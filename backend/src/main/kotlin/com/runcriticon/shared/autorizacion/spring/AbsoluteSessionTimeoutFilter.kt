package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.model.Principal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock
import java.time.Duration

/**
 * Tope absoluto de sesión: pasados [SecuritySessionProperties.sessionAbsoluteMax] (90 días) desde la última
 * autenticación, el usuario se reautentica aunque haya estado activo — la renovación deslizante de 30 días no puede
 * extender una sesión indefinidamente.
 *
 * Contrasta [SessionAuthenticationDetails.authenticatedAt] (escrito en `startSession`) y, si supera el tope o falta
 * (sesión anterior a este control), invalida la sesión ([SecuritySessionManager.endSession]) y responde **401**
 * — fail-closed, como [AccountStatusFilter].
 * Va justo antes de [AccountStatusFilter] en la cadena: una sesión caducada no llega a consultar la proyección de
 * estado de cuenta.
 *
 * Vive en `shared.autorizacion.spring` porque es el único paquete autorizado a tocar el `SecurityContextHolder` (lo
 * verifica `AuthorizationArchTest`).
 */
@Component
class AbsoluteSessionTimeoutFilter(
    private val properties: SecuritySessionProperties,
    private val sessionManager: SecuritySessionManager,
    private val clock: Clock,
) : OncePerRequestFilter() {
    /**
     * Va justo antes de [AccountStatusFilter] en la cadena: una sesión caducada no llega a consultar la proyección de
     * estado de cuenta.
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication != null && authentication.principal is Principal && exceedsAbsoluteMax(authentication)) {
            sessionManager.endSession(request, response)
            response.status = HttpStatus.UNAUTHORIZED.value()
            return
        }
        filterChain.doFilter(request, response)
    }

    /**
     * Contrasta [SessionAuthenticationDetails.authenticatedAt] (escrito en `startSession`) y, si supera el tope o falta
     * (sesión anterior a este control), invalida la sesión ([SecuritySessionManager.endSession]) y responde **401**
     * — fail-closed, como [AccountStatusFilter].
     */
    private fun exceedsAbsoluteMax(authentication: Authentication): Boolean {
        val details =
            authentication.details as? SessionAuthenticationDetails
                // Fail-closed: una sesión autenticada sin marca (anterior a este control) se considera caducada.
                ?: return true
        return Duration.between(details.authenticatedAt, clock.instant()) > properties.sessionAbsoluteMax
    }
}
