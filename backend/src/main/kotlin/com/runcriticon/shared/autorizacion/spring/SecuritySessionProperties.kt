package com.runcriticon.shared.autorizacion.spring

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Timeouts de la sesión de seguridad. Viven bajo `runcriticon.security` y no bajo `spring.session.*` porque sin la
 * autoconfiguración de Spring Session en Boot 4 (cf. [SessionConfig]) las propiedades estándar no llegan al repositorio
 * de sesiones.
 *
 * @property sessionSlidingTimeout expiración deslizante: la sesión caduca tras este tiempo de inactividad y cada uso la
 * renueva. La aplica Spring Session como `MAX_INACTIVE_INTERVAL` (customizer en [SessionConfig]).
 * @property sessionAbsoluteMax tope absoluto: pasado este tiempo desde la última autenticación el usuario se
 * reautentica aunque haya estado activo. Lo aplica [AbsoluteSessionTimeoutFilter].
 */
@ConfigurationProperties("runcriticon.security")
data class SecuritySessionProperties(
    val sessionSlidingTimeout: Duration = DEFAULT_SLIDING_TIMEOUT,
    val sessionAbsoluteMax: Duration = DEFAULT_ABSOLUTE_MAX,
) {
    private companion object {
        val DEFAULT_SLIDING_TIMEOUT: Duration = Duration.ofDays(30)
        val DEFAULT_ABSOLUTE_MAX: Duration = Duration.ofDays(90)
    }
}
