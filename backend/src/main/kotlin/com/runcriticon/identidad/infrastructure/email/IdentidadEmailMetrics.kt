package com.runcriticon.identidad.infrastructure.email

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

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

    fun invitationSent(success: Boolean) {
        if (success) successCounter.increment() else errorCounter.increment()
    }
}
