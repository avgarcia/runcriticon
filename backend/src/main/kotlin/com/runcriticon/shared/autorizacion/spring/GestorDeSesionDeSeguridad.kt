package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.modelo.Principal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component

/**
 * Establece y limpia la sesión de seguridad (ADR-0003 D10, D11). Encapsula toda la manipulación de
 * `SecurityContextHolder`, que solo puede ocurrir en `shared.autorizacion` (lo verifica
 * `AutorizacionArchTest`): así la capa api no toca el contexto de seguridad.
 *
 * El contexto se persiste vía [SecurityContextRepository] en la sesión HTTP, que vive en Postgres
 * (Spring Session JDBC). El cierre delega en [SecurityContextLogoutHandler], que invalida la sesión
 * (revocación inmediata, ADR-0003 D11) sin que esta clase dependa de `HttpSession` directamente.
 *
 * Al autenticar se **rota el id de sesión** ([ChangeSessionIdAuthenticationStrategy]) para prevenir
 * session fixation: un id de sesión fijado antes del login deja de ser válido después de autenticar.
 */
@Component
class GestorDeSesionDeSeguridad(
    private val contextRepository: SecurityContextRepository,
) {
    private val logoutHandler = SecurityContextLogoutHandler()
    private val sessionStrategy = ChangeSessionIdAuthenticationStrategy()

    fun iniciarSesion(
        principal: Principal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val autoridad = SimpleGrantedAuthority("ROLE_${principal.rol.codigo}")
        val autenticacion = UsernamePasswordAuthenticationToken(principal, null, listOf(autoridad))
        // Rota el id de sesión antes de asociar el principal: previene session fixation (sin sesión
        // previa es no-op y saveContext crea una nueva con id propio, igualmente seguro).
        sessionStrategy.onAuthentication(autenticacion, request, response)
        val contexto = SecurityContextHolder.createEmptyContext()
        contexto.authentication = autenticacion
        SecurityContextHolder.setContext(contexto)
        contextRepository.saveContext(contexto, request, response)
    }

    fun cerrarSesion(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        logoutHandler.logout(request, response, SecurityContextHolder.getContext().authentication)
    }
}
