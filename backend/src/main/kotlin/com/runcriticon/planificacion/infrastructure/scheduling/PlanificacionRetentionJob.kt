package com.runcriticon.planificacion.infrastructure.scheduling

import com.runcriticon.planificacion.application.ports.outbound.observability.RetentionMetrics
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Purga de retención de `planificacion.evento_procesado`, con el plazo de las marcas de idempotencia de
 * `club_taxonomia` (ADR-0017 D4): 30 días, la ventana de reentrega del outbox (ADR-0004 D11). Pasada esa ventana
 * ninguna reentrega puede llegar ya, y la marca es inerte. La tabla es `SIN_PII`: es housekeeping del outbox, no
 * ejercicio de un derecho RGPD.
 *
 * `DELETE` idempotente y sin efectos colaterales: puede dispararse en más de una instancia de App Runner a la vez
 * sin necesidad de lock distribuido (ADR-0017 D3).
 */
@Component
class PlanificacionRetentionJob(
    private val jdbc: JdbcTemplate,
    private val metrics: RetentionMetrics,
) {
    @Scheduled(cron = "\${runcriticon.planificacion.retention.cron:0 0 4 * * *}")
    fun purge() {
        val deleted = jdbc.update(EVENTO_PROCESADO_SQL)
        metrics.purged(TABLE_EVENTO_PROCESADO, deleted)
        if (deleted > 0) {
            log.info("Retención planificacion: {} filas purgadas de {}", deleted, TABLE_EVENTO_PROCESADO)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(PlanificacionRetentionJob::class.java)

        const val TABLE_EVENTO_PROCESADO = "evento_procesado"
        const val RETENTION_DAYS = 30

        // Interpolación de una constante de compilación, no de entrada externa: sin riesgo de inyección SQL.
        val EVENTO_PROCESADO_SQL =
            "DELETE FROM planificacion.evento_procesado WHERE processed_at < now() - INTERVAL '$RETENTION_DAYS days'"
    }
}
