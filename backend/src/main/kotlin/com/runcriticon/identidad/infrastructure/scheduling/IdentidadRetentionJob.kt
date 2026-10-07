package com.runcriticon.identidad.infrastructure.scheduling

import com.runcriticon.identidad.application.ports.outbound.observability.RetentionMetrics
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Purga de retención de `identidad.evento_auditoria` (ADR-0017 D8): aplica ADR-0014 D10, categoría 2
 * (auditoría de identidad) — 12 meses. La propia migración (`V202606210001`) ya documentaba esta retención
 * sin que existiera el job que la aplicase.
 *
 * Distinta de `AuditoriaRetentionJob` (`auditoria.evento`, categoría 3, 24 meses): cada módulo purga su
 * propia auditoría local.
 *
 * `DELETE` idempotente y sin efectos colaterales: puede dispararse en más de una instancia de App Runner a la vez
 * sin necesidad de lock distribuido (ADR-0017 D3).
 */
@Component
class IdentidadRetentionJob(
    private val jdbc: JdbcTemplate,
    private val metrics: RetentionMetrics,
) {
    @Scheduled(cron = "\${runcriticon.identidad.retention.cron:0 45 3 * * *}")
    fun purge() {
        val deleted = jdbc.update(EVENTO_AUDITORIA_SQL)
        metrics.purged(TABLE_EVENTO_AUDITORIA, deleted)
        if (deleted > 0) {
            log.info("Retención identidad: {} filas purgadas de {}", deleted, TABLE_EVENTO_AUDITORIA)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(IdentidadRetentionJob::class.java)

        const val TABLE_EVENTO_AUDITORIA = "evento_auditoria"
        const val RETENTION_MONTHS = 12

        // Interpolación de una constante de compilación, no de entrada externa: sin riesgo de inyección SQL.
        val EVENTO_AUDITORIA_SQL =
            "DELETE FROM identidad.evento_auditoria WHERE ts < now() - INTERVAL '$RETENTION_MONTHS months'"
    }
}
