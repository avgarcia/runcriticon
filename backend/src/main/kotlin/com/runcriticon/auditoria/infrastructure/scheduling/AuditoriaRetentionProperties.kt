package com.runcriticon.auditoria.infrastructure.scheduling

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuración del job de retención de `auditoria` (ADR-0017 D5, D8): purga `auditoria.evento` a los 24 meses
 * (ADR-0014 D10, categoría 3).
 *
 * @property cron expresión cron de 6 campos (seg min hora día mes día-semana). Por defecto, madrugada.
 */
@ConfigurationProperties("runcriticon.auditoria.retention")
data class AuditoriaRetentionProperties(
    val cron: String = DEFAULT_CRON,
) {
    private companion object {
        const val DEFAULT_CRON = "0 30 3 * * *"
    }
}
