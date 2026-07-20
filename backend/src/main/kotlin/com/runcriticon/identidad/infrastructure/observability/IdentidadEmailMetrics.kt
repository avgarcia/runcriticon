package com.runcriticon.identidad.infrastructure.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Métricas de envío de emails del módulo identidad. Expone el counter `identidad.email.invitations.sent` con tags
 * controlados `module` y `result` (`success`/`error`), evitando cardinalidad alta (sin `user_id` ni emails).
 */
@Component
class IdentidadEmailMetrics(
    registry: MeterRegistry,
) {
    private val successCounter: Counter =
        Counter
            .builder("identidad.email.invitations.sent")
            .tag("module", "identidad")
            .tag("result", "success")
            .register(registry)

    private val errorCounter: Counter =
        Counter
            .builder("identidad.email.invitations.sent")
            .tag("module", "identidad")
            .tag("result", "error")
            .register(registry)

    private val magicLinkSuccessCounter: Counter =
        Counter
            .builder("identidad.email.magic_links.sent")
            .tag("module", "identidad")
            .tag("result", "success")
            .register(registry)

    private val magicLinkErrorCounter: Counter =
        Counter
            .builder("identidad.email.magic_links.sent")
            .tag("module", "identidad")
            .tag("result", "error")
            .register(registry)

    private val passwordResetSuccessCounter: Counter =
        Counter
            .builder("identidad.email.password_resets.sent")
            .tag("module", "identidad")
            .tag("result", "success")
            .register(registry)

    private val passwordResetErrorCounter: Counter =
        Counter
            .builder("identidad.email.password_resets.sent")
            .tag("module", "identidad")
            .tag("result", "error")
            .register(registry)

    /** Incrementa el counter de invitaciones enviadas según el resultado del envío. */
    fun invitationSent(success: Boolean) {
        (if (success) successCounter else errorCounter).increment()
    }

    /** Incrementa el counter de magic links enviados según el resultado del envío. */
    fun magicLinkSent(success: Boolean) {
        (if (success) magicLinkSuccessCounter else magicLinkErrorCounter).increment()
    }

    /** Incrementa el counter de reseteos de contraseña enviados según el resultado del envío. */
    fun passwordResetSent(success: Boolean) {
        (if (success) passwordResetSuccessCounter else passwordResetErrorCounter).increment()
    }
}
