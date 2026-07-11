package com.runcriticon.shared.observability

import com.runcriticon.shared.autorizacion.PrincipalProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerMapping

/**
 * Rellena el MDC en cada petición HTTP (ADR-0011 D5): la mitad que faltaba junto a
 * [MdcRestorerForEvents], que solo cubre los `@ApplicationModuleListener`. Sin esto, los logs
 * generados durante una petición HTTP no llevan `trace_id`/`club_id`/`user_id_hash`/`module`.
 *
 * - `trace_id`: del `traceparent` W3C entrante si lo hay (reutiliza el parseo de
 *   [MdcRestorerForEvents.restore]); si no, no se rellena (no generamos un traceparent propio aquí,
 *   fuera de alcance sin un SDK de tracing real, ADR-0011 D4).
 * - `club_id` / `user_id_hash`: del [PrincipalProvider] si la petición está autenticada; en rutas
 *   anónimas (login, activación, health) no hay principal — `user_id_hash` cae a `"system"`, igual
 *   que en el lado de eventos.
 * - `module`: del paquete del controller resuelto por Spring MVC (`HandlerMapping.getHandler`,
 *   igual mecanismo que usa `DispatcherServlet` internamente) — `"unmatched"` si no hay ruta (404).
 * - `env`: primer perfil Spring activo; `"unknown"` si no hay ninguno activo (no debería pasar fuera
 *   de tests unitarios sin contexto).
 *
 * Se registra tras `SecurityContextHolderFilter`, igual que
 * [com.runcriticon.shared.autorizacion.spring.AbsoluteSessionTimeoutFilter], para que el contexto
 * de autenticación ya esté cargado cuando se resuelve el principal.
 */
@Component
class HttpMdcFilter(
    private val mdcRestorer: MdcRestorerForEvents,
    private val principalProvider: PrincipalProvider,
    private val handlerMappings: List<HandlerMapping>,
    private val environment: Environment,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            val principal = runCatching { principalProvider.current() }.getOrNull()
            mdcRestorer.restore(
                module = moduleOf(request),
                traceparent = request.getHeader("traceparent"),
                clubId = principal?.clubId,
                actorId = principal?.userId,
            )
            MDC.put("env", environment.activeProfiles.firstOrNull() ?: "unknown")
            filterChain.doFilter(request, response)
        } finally {
            mdcRestorer.clear()
        }
    }

    /** Resuelve el controller que atenderá la petición (sin invocarlo) para derivar `module`. */
    private fun moduleOf(request: HttpServletRequest): String {
        val handler =
            handlerMappings.firstNotNullOfOrNull { mapping ->
                runCatching { mapping.getHandler(request) }.getOrNull()?.handler
            }
        val controllerClass = (handler as? HandlerMethod)?.beanType ?: return "unmatched"
        val rootPackage =
            controllerClass.packageName
                .removePrefix("com.runcriticon.")
                .substringBefore(".")
        return ModuleTagResolver.resolve(rootPackage)
    }
}
