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

/**
 * Configuración de Spring Security (ADR-0003 D1, D10, D13, D14):
 *  - **CSRF** activado con cookie legible por la SPA (Angular envía `X-XSRF-TOKEN`); el handler
 *    plano evita el cifrado BREACH que rompería el cliente (D14).
 *  - **Sesión** por cookie respaldada en Postgres vía Spring Session JDBC (D10): el contexto de
 *    seguridad se persiste con [HttpSessionSecurityContextRepository].
 *  - **Argon2id** como [PasswordEncoder] (D13), con parámetros configurables ([Argon2Properties]).
 *  - **Cabeceras de seguridad** (LAL-58): CSP restrictiva, Referrer-Policy y HSTS.
 *  - Rutas públicas mínimas: login y health check; el resto exige sesión.
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
            // MDC operativo (ADR-0011 D5): primero de todos para que absoluteSessionTimeoutFilter y
            // accountStatusFilter (que pueden rechazar la petición) también logueen correlados.
            .addFilterAfter(httpMdcFilter, SecurityContextHolderFilter::class.java)
            // Tope absoluto de sesión (ADR-0003 D10, LAL-57): tras cargar el contexto de seguridad,
            // expulsa (401) las sesiones con más de 90 días desde la autenticación.
            .addFilterAfter(absoluteSessionTimeoutFilter, HttpMdcFilter::class.java)
            // Gate-check de estado (ADR-0003 D11): rechaza (401) toda petición cuyo principal ya no
            // esté ACTIVO (cuenta desactivada con sesión superviviente). Va tras el tope absoluto:
            // una sesión caducada no llega a consultar la proyección de estado.
            .addFilterAfter(accountStatusFilter, AbsoluteSessionTimeoutFilter::class.java)
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/sesion").permitAll()
                // Reseteo de contraseña anónimo (ADR-0003 D8): solicitud (202 neutro) y consumo.
                auth.requestMatchers(HttpMethod.POST, "/api/sesion/reseteo").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/sesion/reseteo/consumo").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/activacion").permitAll()
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                auth.anyRequest().authenticated()
            }.headers { headers ->
                // CSP (LAL-58): defensa principal anti-XSS de la SPA same-origin. style-src lleva
                // 'unsafe-inline' porque Angular inyecta los estilos de componente como <style> sin
                // nonce (ngCspNonce exigiría servir un index.html dinámico, fuera del MVP).
                headers.contentSecurityPolicy { it.policyDirectives(CSP_POLICY) }
                headers.referrerPolicy { it.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN) }
                // HSTS solo se emite en peticiones seguras: en producción llega como https vía
                // X-Forwarded-Proto (forward-headers-strategy, ADR-0006); en local http no sale.
                headers.httpStrictTransportSecurity {
                    it.maxAgeInSeconds(HSTS_MAX_AGE_SECONDS).includeSubDomains(true)
                }
            }.formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            // API/SPA: sin sesión se responde 401 (no 403, el default al desactivar formLogin).
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .addFilterAfter(CsrfCookieFilter(), CsrfFilter::class.java)
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
