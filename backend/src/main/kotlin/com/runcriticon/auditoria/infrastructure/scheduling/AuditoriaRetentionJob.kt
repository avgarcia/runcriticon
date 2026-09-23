package com.runcriticon.auditoria.infrastructure.scheduling

import com.runcriticon.auditoria.application.ports.outbound.observability.RetentionMetrics
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Purga de retención de `auditoria.evento` (ADR-0017 D5): aplica directamente ADR-0014 D10,
 * categoría 3 (auditoría de autorización) — 24 meses.
 *
 * Independiente de `AuditTrailAnonymizationListener` (que anonimiza `actor_id`/`sujeto_id` al recibir
 * `AlumnoEliminado`/`EntrenadorEliminado`): esta purga borra la fila entera pasados 24 meses, esté o no ya
 * anonimizada.
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
        val deleted = jdbc.update(EVENTO_SQL)
        metrics.purged(TABLE_EVENTO, deleted)
        if (deleted > 0) {
            log.info("Retención auditoria: {} filas purgadas de {}", deleted, TABLE_EVENTO)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AuditoriaRetentionJob::class.java)

        const val TABLE_EVENTO = "evento"
        const val RETENTION_MONTHS = 24

        // Interpolación de una constante de compilación, no de entrada externa: sin riesgo de inyección SQL.
        val EVENTO_SQL =
            "DELETE FROM auditoria.evento WHERE ts < now() - INTERVAL '$RETENTION_MONTHS months'"
    }
}
