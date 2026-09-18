package com.runcriticon.clubtaxonomia.infrastructure.scheduling

import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.RetentionMetrics
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Purga de retención de `club_taxonomia` (ADR-0017 D4, cierra LAL-107). Las lápidas de supresión
 * (`persona_eliminada`) y las marcas de idempotencia de listeners (`evento_procesado`) solo hacen falta mientras
 * pueda llegar un evento rezagado del outbox (ADR-0004 D11, 30 días); pasada esa ventana son inertes.
 *
 * `DELETE` idempotente y sin efectos colaterales: puede dispararse en más de una instancia de App Runner a la vez
 * sin necesidad de lock distribuido (ADR-0017 D3).
 */
@Component
class ClubTaxonomiaRetentionJob(
    private val jdbc: JdbcTemplate,
    private val metrics: RetentionMetrics,
) {
    @Scheduled(cron = "\${runcriticon.club-taxonomia.retention.cron:0 0 3 * * *}")
    fun purge() {
        purgeTable(PERSONA_ELIMINADA_SQL, TABLE_PERSONA_ELIMINADA)
        purgeTable(EVENTO_PROCESADO_SQL, TABLE_EVENTO_PROCESADO)
    }

    private fun purgeTable(
        sql: String,
        table: String,
    ) {
        val deleted = jdbc.update(sql)
        metrics.purged(table, deleted)
        if (deleted > 0) {
            log.info("Retención club_taxonomia: {} filas purgadas de {}", deleted, table)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(ClubTaxonomiaRetentionJob::class.java)

        const val TABLE_PERSONA_ELIMINADA = "persona_eliminada"
        const val TABLE_EVENTO_PROCESADO = "evento_procesado"
        const val RETENTION_DAYS = 30

        // Interpolación de una constante de compilación, no de entrada externa: sin riesgo de inyección SQL.
        val PERSONA_ELIMINADA_SQL =
            "DELETE FROM club_taxonomia.persona_eliminada WHERE eliminado_en < now() - INTERVAL '$RETENTION_DAYS days'"
        val EVENTO_PROCESADO_SQL =
            "DELETE FROM club_taxonomia.evento_procesado WHERE processed_at < now() - INTERVAL '$RETENTION_DAYS days'"
    }
}
