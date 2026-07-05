package com.runcriticon.shared.autorizacion.spring

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.config.SessionRepositoryCustomizer
import org.springframework.session.jdbc.JdbcIndexedSessionRepository
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer

/**
 * Activa **explícitamente** Spring Session JDBC (ADR-0003 D10): la sesión `httpOnly` se respalda en
 * Postgres (tabla `SPRING_SESSION`, creada por Flyway en `_shared`, no por el framework).
 *
 * Se declara de forma explícita en vez de confiar solo en `spring.session.store-type: jdbc` —cuyo
 * manejo por autoconfiguración cambió en Spring Boot 4 y no materializaba el bean en el contexto de
 * `@SpringBootTest`— para **garantizar** el bean
 * [org.springframework.session.FindByIndexNameSessionRepository] en todos los contextos (producción y
 * tests). De él depende [SpringSessionRevoker] para invalidar todas las sesiones de un usuario
 * (D7 cambio de contraseña, D8 reseteo, D11 revocación por admin). El nombre de tabla por defecto
 * (`SPRING_SESSION`) coincide con el DDL de la migración.
 */
@Configuration
@EnableJdbcHttpSession
@EnableConfigurationProperties(SecuritySessionProperties::class)
class SessionConfig {
    /**
     * Atributos de la cookie de sesión fijados en código (ADR-0003 D10, LAL-56): `Secure`,
     * `HttpOnly` y `SameSite=Lax` explícitos, sin depender de `request.isSecure()` (falso tras el
     * proxy TLS de App Runner) ni de defaults del framework. Va como bean porque en Spring Boot 4
     * la autoconfiguración de Spring Session no está en el classpath (misma razón que
     * `@EnableJdbcHttpSession` arriba), así que `server.servlet.session.cookie.*` NO llega a este
     * serializer. `Secure` es incondicional también en local: los navegadores aceptan cookies
     * `Secure` sobre `http://localhost` (origen trustworthy).
     */
    @Bean
    fun cookieSerializer(): CookieSerializer =
        DefaultCookieSerializer().apply {
            setUseSecureCookie(true)
            setUseHttpOnlyCookie(true)
            setSameSite("Lax")
        }

    /**
     * Expiración deslizante de 30 días (ADR-0003 D10, LAL-57): cada uso renueva la sesión; sin
     * actividad, caduca (`MAX_INACTIVE_INTERVAL`). Va como customizer y no como
     * `spring.session.timeout` por la misma razón que la cookie de arriba: sin la autoconfiguración
     * de Spring Session, esa propiedad no llega al repositorio. El tope absoluto de 90 días lo
     * aplica [AbsoluteSessionTimeoutFilter] por petición.
     */
    @Bean
    fun sessionSlidingTimeoutCustomizer(
        properties: SecuritySessionProperties,
    ): SessionRepositoryCustomizer<JdbcIndexedSessionRepository> =
        SessionRepositoryCustomizer { repository ->
            repository.setDefaultMaxInactiveInterval(properties.sessionSlidingTimeout)
        }
}
