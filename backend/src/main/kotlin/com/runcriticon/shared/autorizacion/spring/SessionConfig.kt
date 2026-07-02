package com.runcriticon.shared.autorizacion.spring

import org.springframework.context.annotation.Configuration
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession

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
class SessionConfig
