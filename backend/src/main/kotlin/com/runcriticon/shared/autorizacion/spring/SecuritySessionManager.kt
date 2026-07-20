package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.model.Principal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Establece y limpia la sesión de seguridad. Encapsula toda la manipulación de `SecurityContextHolder`, que solo puede
 * ocurrir en `shared.autorizacion` (lo verifica `AuthorizationArchTest`): así la capa api no toca el contexto de
 * seguridad.
 *
 * El contexto se persiste vía [SecurityContextRepository] en la sesión HTTP, que vive en Postgres (Spring Session
 * JDBC). Al autenticar se guarda además, como `details` del token, [SessionAuthenticationDetails] con el instante de
 * autenticación que [AbsoluteSessionTimeoutFilter] contrasta con el tope absoluto de 90 días. El cierre delega en
 * [SecurityContextLogoutHandler], que invalida la sesión sin que esta clase dependa de `HttpSession` directamente.
 *
 * Al autenticar se **rota el id de sesión** ([ChangeSessionIdAuthenticationStrategy]) para prevenir session fixation:
 * un id de sesión fijado antes del login deja de ser válido después de autenticar.
 */
@Component
class SecuritySessionManager(
    private val contextRepository: SecurityContextRepository,
    private val clock: Clock,
) {
    private val logoutHandler = SecurityContextLogoutHandler()
    private val sessionStrategy = ChangeSessionIdAuthenticationStrategy()

    fun startSession(
        principal: Principal,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val authority = SimpleGrantedAuthority("ROLE_${principal.role.code}")

        val authentication = UsernamePasswordAuthenticationToken(principal, null, listOf(authority))
        authentication.details = SessionAuthenticationDetails(authenticatedAt = clock.instant())

        sessionStrategy.onAuthentication(authentication, request, response)

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication

        SecurityContextHolder.setContext(context)
        contextRepository.saveContext(context, request, response)
    }

    fun endSession(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        logoutHandler.logout(request, response, SecurityContextHolder.getContext().authentication)
    }
}
