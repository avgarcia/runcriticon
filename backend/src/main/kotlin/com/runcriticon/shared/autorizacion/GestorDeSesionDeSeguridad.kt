package com.runcriticon.shared.autorizacion

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
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
 */
@Component
class GestorDeSesionDeSeguridad(
    private val contextRepository: SecurityContextRepository,
) {
    private val logoutHandler = SecurityContextLogoutHandler()

    fun iniciarSesion(
        principal: Principal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val autoridad = SimpleGrantedAuthority("ROLE_${principal.rol.codigo}")
        val autenticacion = UsernamePasswordAuthenticationToken(principal, null, listOf(autoridad))
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
