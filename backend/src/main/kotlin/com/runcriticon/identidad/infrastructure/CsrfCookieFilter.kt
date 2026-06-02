package com.runcriticon.identidad.infrastructure

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Fuerza la materialización del token CSRF en cada respuesta (ADR-0003 D14). Spring Security 6
 * difiere la carga del token, así que la cookie `XSRF-TOKEN` no se emite hasta que algo lo lee;
 * acceder a `.token` aquí garantiza que la SPA reciba la cookie y pueda reenviarla en `X-XSRF-TOKEN`
 * en el siguiente POST. Patrón oficial de Spring Security para SPAs.
 */
class CsrfCookieFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val csrfToken = request.getAttribute(CsrfToken::class.java.name) as? CsrfToken
        // Renderiza el token (y con él la cookie XSRF-TOKEN) antes de continuar la cadena.
        csrfToken?.token
        filterChain.doFilter(request, response)
    }
}
