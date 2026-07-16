package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.AccountActivePort
import com.runcriticon.shared.autorizacion.model.Principal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Gate-check de estado de cuenta. Para cada petición **autenticada**, verifica contra la proyección de estado
 * ([AccountActivePort]) que el principal siga activo; si su cuenta ha pasado a `DESACTIVADO` (y su sesión sobrevivió a
 * la revocación), invalida la sesión ([SecuritySessionManager.endSession]) y responde **401**, sin dejar continuar la
 * cadena.
 *
 * Verifica el estado del usuario al renovar la cookie: la desactivación ya revoca las sesiones de forma proactiva, pero
 * este filtro es la barrera *fail-closed* que cubre cualquier sesión que sobreviva a la revocación.
 *
 * Vive en `shared.autorizacion.spring` porque es el único paquete autorizado a tocar el `SecurityContextHolder` (lo
 * verifica `AuthorizationArchTest`). No toca `HttpSession` directamente: delega el cierre en [SecuritySessionManager],
 * que usa el `SecurityContextLogoutHandler`.
 */
@Component
class AccountStatusFilter(
    private val accountActivePort: AccountActivePort,
    private val sessionManager: SecuritySessionManager,
) : OncePerRequestFilter() {
    /**
     * Contrasta [AccountActivePort.isActive] y, si el principal está desactivado, invalida la sesión
     * ([SecuritySessionManager.endSession]) y responde **401** — fail-closed, como [AbsoluteSessionTimeoutFilter].
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? Principal
        if (principal != null && !accountActivePort.isActive(principal.userId)) {
            sessionManager.endSession(request, response)
            response.status = HttpStatus.UNAUTHORIZED.value()
            return
        }
        filterChain.doFilter(request, response)
    }
}
