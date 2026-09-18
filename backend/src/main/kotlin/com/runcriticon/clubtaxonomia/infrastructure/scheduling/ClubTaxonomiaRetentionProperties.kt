package com.runcriticon.clubtaxonomia.infrastructure.scheduling

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuración del job de retención de `club_taxonomia` (ADR-0017 D4, D8): purga `persona_eliminada` y
 * `evento_procesado` pasada la ventana de reentrega del outbox.
 *
 * @property cron expresión cron de 6 campos (seg min hora día mes día-semana). Por defecto, madrugada.
 */
@ConfigurationProperties("runcriticon.club-taxonomia.retention")
data class ClubTaxonomiaRetentionProperties(
    val cron: String = DEFAULT_CRON,
) {
    private companion object {
        const val DEFAULT_CRON = "0 0 3 * * *"
    }
}
