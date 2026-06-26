package com.runcriticon.identidad.infrastructure.security

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
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler

/**
 * Configuración de Spring Security (ADR-0003 D1, D10, D13, D14):
 *  - **CSRF** activado con cookie legible por la SPA (Angular envía `X-XSRF-TOKEN`); el handler
 *    plano evita el cifrado BREACH que rompería el cliente (D14).
 *  - **Sesión** por cookie respaldada en Postgres vía Spring Session JDBC (D10): el contexto de
 *    seguridad se persiste con [HttpSessionSecurityContextRepository].
 *  - **Argon2id** como [PasswordEncoder] (D13).
 *  - Rutas públicas mínimas: login y health check; el resto exige sesión.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        contextRepository: SecurityContextRepository,
    ): SecurityFilterChain {
        http
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                csrf.csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
            }.securityContext { it.securityContextRepository(contextRepository) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/sesion").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/api/activacion").permitAll()
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                auth.anyRequest().authenticated()
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
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
}
