package com.runcriticon.identidad.infrastructure.scheduling

import com.runcriticon.identidad.application.ports.outbound.observability.RetentionMetrics
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Purga de retención de `identidad` (ADR-0017 D8). `evento_auditoria` aplica ADR-0014 D10, categoría 2
 * (auditoría de identidad) — 12 meses. La propia migración (`V202606210001`) ya documentaba esta retención
 * sin que existiera el job que la aplicase.
 *
 * `evento_procesado` sigue el plazo de las marcas de idempotencia de `club_taxonomia` (ADR-0017 D4): 30 días,
 * la ventana de reentrega del outbox (ADR-0004 D11). Pasada esa ventana ninguna reentrega puede llegar ya, y la
 * marca es inerte.
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
        purgeTable(EVENTO_AUDITORIA_SQL, TABLE_EVENTO_AUDITORIA)
        purgeTable(EVENTO_PROCESADO_SQL, TABLE_EVENTO_PROCESADO)
    }

    private fun purgeTable(
        sql: String,
        table: String,
    ) {
        val deleted = jdbc.update(sql)
        metrics.purged(table, deleted)
        if (deleted > 0) {
            log.info("Retención identidad: {} filas purgadas de {}", deleted, table)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(IdentidadRetentionJob::class.java)

        const val TABLE_EVENTO_AUDITORIA = "evento_auditoria"
        const val TABLE_EVENTO_PROCESADO = "evento_procesado"
        const val RETENTION_MONTHS = 12
        const val RETENTION_DAYS = 30

        // Interpolación de una constante de compilación, no de entrada externa: sin riesgo de inyección SQL.
        val EVENTO_AUDITORIA_SQL =
            "DELETE FROM identidad.evento_auditoria WHERE ts < now() - INTERVAL '$RETENTION_MONTHS months'"
        val EVENTO_PROCESADO_SQL =
            "DELETE FROM identidad.evento_procesado WHERE processed_at < now() - INTERVAL '$RETENTION_DAYS days'"
    }
}
