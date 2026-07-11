package com.runcriticon.identidad.infrastructure.observability

import com.runcriticon.identidad.application.ports.BusinessMetrics
import com.runcriticon.shared.autorizacion.model.Role
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Implementación Micrometer del puerto [BusinessMetrics] (ADR-0011, catálogo en
 * `docs/arquitectura/observabilidad-por-modulo.md` §7). Expone `identidad.accounts.activated`,
 * tags `module` y `role` (`ALUMNO`/`ENTRENADOR`; cardinalidad baja, sin `user_id`).
 *
 * Primer contador del catálogo instrumentado; el resto (magic links, invitaciones,
 * time-to-activation) queda pendiente — ver ADR-0015.
 */
@Component
class IdentidadBusinessMetrics(
    registry: MeterRegistry,
) : BusinessMetrics {
    private val alumnoActivatedCounter: Counter =
        Counter
            .builder("identidad.accounts.activated")
            .tag("module", "identidad")
            .tag("role", Role.ALUMNO.code)
            .register(registry)

    private val entrenadorActivatedCounter: Counter =
        Counter
            .builder("identidad.accounts.activated")
            .tag("module", "identidad")
            .tag("role", Role.ENTRENADOR.code)
            .register(registry)

    /** Incrementa el counter de cuentas activadas según el rol (ADMIN no pasa por activación). */
    override fun accountActivated(role: Role) {
        when (role) {
            Role.ALUMNO -> alumnoActivatedCounter.increment()
            Role.ENTRENADOR -> entrenadorActivatedCounter.increment()
            Role.ADMIN -> Unit
        }
    }
}
