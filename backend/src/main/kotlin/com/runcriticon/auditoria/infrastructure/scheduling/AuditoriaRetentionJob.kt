package com.runcriticon.auditoria.infrastructure.scheduling

import com.runcriticon.auditoria.application.ports.outbound.observability.RetentionMetrics
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Purga de retención de `auditoria`. `auditoria.evento` aplica directamente ADR-0014 D10, categoría 3 (auditoría
 * de autorización) — 24 meses (ADR-0017 D5).
 *
 * Independiente de `AuditTrailAnonymizationListener` (que anonimiza `actor_id`/`sujeto_id` al recibir
 * `AlumnoEliminado`/`EntrenadorEliminado`): esta purga borra la fila entera pasados 24 meses, esté o no ya
 * anonimizada.
 *
 * `evento_procesado` sigue el plazo de las marcas de idempotencia de `club_taxonomia` (ADR-0017 D4): 30 días,
 * la ventana de reentrega del outbox (ADR-0004 D11). Pasada esa ventana ninguna reentrega puede llegar ya, y la
 * marca es inerte.
 *
 * `DELETE` idempotente y sin efectos colaterales: puede dispararse en más de una instancia de App Runner a la vez
 * sin necesidad de lock distribuido (ADR-0017 D3).
 */
@Component
class AuditoriaRetentionJob(
    private val jdbc: JdbcTemplate,
    private val metrics: RetentionMetrics,
) {
    @Scheduled(cron = "\${runcriticon.auditoria.retention.cron:0 30 3 * * *}")
    fun purge() {
        purgeTable(EVENTO_SQL, TABLE_EVENTO)
        purgeTable(EVENTO_PROCESADO_SQL, TABLE_EVENTO_PROCESADO)
    }

    private fun purgeTable(
        sql: String,
        table: String,
    ) {
        val deleted = jdbc.update(sql)
        metrics.purged(table, deleted)
        if (deleted > 0) {
            log.info("Retención auditoria: {} filas purgadas de {}", deleted, table)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AuditoriaRetentionJob::class.java)

        const val TABLE_EVENTO = "evento"
        const val TABLE_EVENTO_PROCESADO = "evento_procesado"
        const val RETENTION_MONTHS = 24
        const val RETENTION_DAYS = 30

        // Interpolación de una constante de compilación, no de entrada externa: sin riesgo de inyección SQL.
        val EVENTO_SQL =
            "DELETE FROM auditoria.evento WHERE ts < now() - INTERVAL '$RETENTION_MONTHS months'"
        val EVENTO_PROCESADO_SQL =
            "DELETE FROM auditoria.evento_procesado WHERE processed_at < now() - INTERVAL '$RETENTION_DAYS days'"
    }
}
