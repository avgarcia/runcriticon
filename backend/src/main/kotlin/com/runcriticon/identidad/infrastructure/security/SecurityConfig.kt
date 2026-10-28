package com.runcriticon.identidad.infrastructure.security

import com.runcriticon.shared.autorizacion.spring.AbsoluteSessionTimeoutFilter
import com.runcriticon.shared.autorizacion.spring.AccountStatusFilter
import com.runcriticon.shared.observability.HttpMdcFilter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.AndRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * Configuración de Spring Security:
 *  - **CSRF** activado con cookie legible por la SPA (Angular envía `X-XSRF-TOKEN`); el handler plano evita el cifrado
 *    BREACH que rompería el cliente.
 *  - **Sesión** por cookie respaldada en Postgres vía Spring Session JDBC: el contexto de seguridad se persiste con
 *    [HttpSessionSecurityContextRepository].
 *  - **Argon2id** como [PasswordEncoder], con parámetros configurables ([Argon2Properties]).
 *  - **Cabeceras de seguridad**: CSP restrictiva, Referrer-Policy y HSTS.
 *  - Rutas públicas mínimas: login, reseteo, activación, health check y la carcasa estática de la SPA (`GET` fuera de
 *    `/api` y `/actuator`: `index.html`, los bundles y los enlaces profundos que resuelve el router de Angular, que
 *    no contienen datos). Todo `/api` sigue exigiendo sesión salvo las excepciones explícitas.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(Argon2Properties::class)
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        contextRepository: SecurityContextRepository,
        httpMdcFilter: HttpMdcFilter,
        absoluteSessionTimeoutFilter: AbsoluteSessionTimeoutFilter,
        accountStatusFilter: AccountStatusFilter,
    ): SecurityFilterChain {
        http
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                csrf.csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
            }.securityContext { it.securityContextRepository(contextRepository) }
            // MDC operativo: primero de todos para que absoluteSessionTimeoutFilter y accountStatusFilter (que pueden
            // rechazar la petición) también logueen correlados.
            .addFilterAfter(httpMdcFilter, SecurityContextHolderFilter::class.java)
            // Tope absoluto de sesión: tras cargar el contexto de seguridad, expulsa (401) las sesiones con más de 90
            // días desde la autenticación.
            .addFilterAfter(absoluteSessionTimeoutFilter, HttpMdcFilter::class.java)
            // Gate-check de estado: rechaza (401) toda petición cuyo principal ya no esté ACTIVO (cuenta desactivada
            // con sesión superviviente). Va tras el tope absoluto: una sesión caducada no llega a consultar la
            // proyección de estado.
            .addFilterAfter(accountStatusFilter, AbsoluteSessionTimeoutFilter::class.java)
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/sesion").permitAll()
                // Reseteo de contraseña anónimo: solicitud (202 neutro) y consumo.
                auth.requestMatchers(HttpMethod.POST, "/api/sesion/reseteo").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/sesion/reseteo/consumo").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/activacion").permitAll()
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // Cambio de nivel de log en runtime: solo ADMIN, nunca alumno/entrenador.
                auth.requestMatchers("/actuator/loggers", "/actuator/loggers/**").hasRole("ADMIN")
                // Sin esto, un anónimo no puede cargar ni la pantalla de login ni el enlace de activación de su email:
                // la SPA se sirve desde esta misma app (ADR-0001), no desde `ng serve`.
                auth.requestMatchers(SPA_SHELL).permitAll()
                auth.anyRequest().authenticated()
            }.headers { headers ->
                // CSP: defensa principal anti-XSS de la SPA same-origin. style-src lleva 'unsafe-inline' porque Angular
                // inyecta los estilos de componente como <style> sin nonce).
                headers.contentSecurityPolicy { it.policyDirectives(CSP_POLICY) }
                headers.referrerPolicy { it.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
                // HSTS solo se emite en peticiones seguras: en producción llega como https vía X-Forwarded-Proto;
                // en local http no sale.
                headers.httpStrictTransportSecurity {
                    it.maxAgeInSeconds(HSTS_MAX_AGE_SECONDS).includeSubDomains(true)
                }
            }.formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            // API/SPA: sin sesión se responde 401 (no 403, el default al desactivar formLogin). El accessDeniedHandler
            // se fija a mano (sin sendError, que dispara el forward interno a /error y aquí termina resolviendo 401):
            // una petición autenticada pero sin el rol exigido (hasRole) debe responder 403, verificado empíricamente
            // con ActuatorLoggersAuthorizationIntegrationTest.
            .exceptionHandling {
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                it.accessDeniedHandler { _, response, _ -> response.status = HttpStatus.FORBIDDEN.value() }
            }.addFilterAfter(CsrfCookieFilter(), CsrfFilter::class.java)
        return http.build()
    }

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun passwordEncoder(argon2: Argon2Properties): PasswordEncoder =
        Argon2PasswordEncoder(
            argon2.saltLength,
            argon2.hashLength,
            argon2.parallelism,
            argon2.memoryKb,
            argon2.iterations,
        )

    private companion object {
        /**
         * `GET` fuera de `/api` y `/actuator`: la carcasa estática de la SPA. Por negación con los matchers del
         * framework (misma normalización de rutas que el resto de reglas), así que todo controlador tiene que vivir
         * bajo `/api`; `SpaShellIntegrationTest` falla si alguno se mapea fuera, porque quedaría público.
         */
        val SPA_SHELL: RequestMatcher =
            PathPatternRequestMatcher.withDefaults().let { paths ->
                AndRequestMatcher(
                    paths.matcher(HttpMethod.GET, "/**"),
                    NegatedRequestMatcher(OrRequestMatcher(paths.matcher("/api/**"), paths.matcher("/actuator/**"))),
                )
            }

        /** Un año: recomendación OWASP y mínimo de las listas de preload. */
        const val HSTS_MAX_AGE_SECONDS = 31_536_000L

        val CSP_POLICY =
            listOf(
                "default-src 'self'",
                "script-src 'self'",
                "style-src 'self' 'unsafe-inline'",
                "img-src 'self' data:",
                "font-src 'self'",
                "connect-src 'self'",
                "object-src 'none'",
                "base-uri 'self'",
                "frame-ancestors 'none'",
                "form-action 'self'",
            ).joinToString("; ")
    }
}
