package com.runcriticon.shared.events.infrastructure.scheduling

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuración del job de retención del outbox compartido `event_publication` (ADR-0017 D6, D8; cierra el
 * hueco real de ADR-0004 D11).
 *
 * @property cron expresión cron de 6 campos (seg min hora día mes día-semana). Por defecto, madrugada.
 */
@ConfigurationProperties("runcriticon.events.retention")
data class EventPublicationRetentionProperties(
    val cron: String = DEFAULT_CRON,
) {
    private companion object {
        const val DEFAULT_CRON = "0 15 3 * * *"
    }
}
