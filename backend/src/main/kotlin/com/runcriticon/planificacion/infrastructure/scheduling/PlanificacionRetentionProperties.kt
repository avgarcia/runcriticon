package com.runcriticon.planificacion.infrastructure.scheduling

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuración del job de retención de `planificacion` (ADR-0017 D8): purga `evento_procesado` pasada la ventana
 * de reentrega del outbox.
 *
 * @property cron expresión cron de 6 campos (seg min hora día mes día-semana). Por defecto, madrugada.
 */
@ConfigurationProperties("runcriticon.planificacion.retention")
data class PlanificacionRetentionProperties(
    val cron: String = DEFAULT_CRON,
) {
    private companion object {
        const val DEFAULT_CRON = "0 0 4 * * *"
    }
}
