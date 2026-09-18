package com.runcriticon.shared.events.infrastructure.scheduling

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Purga de retención del outbox compartido `event_publication` (ADR-0017 D6). Cierra LAL-134 y el hueco real
 * detrás de ADR-0004 D11: ese ADR asumía un job de retención "cubierto por tests" que nunca llegó a
 * implementarse.
 *
 * `event_publication` no pertenece a ningún módulo (es la tabla del outbox de Spring Modulith, ADR-0007 D6), así
 * que este job vive en `shared.events`, no en un módulo.
 *
 * La guarda `completion_date IS NOT NULL` es **la condición de seguridad de todo este job**: una fila con
 * `completion_date` nulo sigue pendiente o fallada (DLQ implícita, ADR-0007 D13) y nunca debe tocarse — no se
 * relaja jamás, ni siquiera para depurar.
 *
 * `DELETE` idempotente y sin efectos colaterales: puede dispararse en más de una instancia de App Runner a la vez
 * sin necesidad de lock distribuido (ADR-0017 D3).
 */
@Component
class EventPublicationRetentionJob(
    private val jdbc: JdbcTemplate,
    registry: MeterRegistry,
) {
    private val rowsDeletedCounter: Counter =
        Counter
            .builder("shared.events.retention_purge.rows_deleted")
            .description("Filas de event_publication purgadas por el job de retención (ADR-0017)")
            .register(registry)

    @Scheduled(cron = "\${runcriticon.events.retention.cron:0 15 3 * * *}")
    fun purge() {
        val deleted = jdbc.update(PURGE_SQL)
        rowsDeletedCounter.increment(deleted.toDouble())
        if (deleted > 0) {
            log.info("Retención event_publication: {} filas purgadas", deleted)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(EventPublicationRetentionJob::class.java)
        const val RETENTION_DAYS = 30

        // Interpolación de una constante de compilación, no de entrada externa: sin riesgo de inyección SQL.
        val PURGE_SQL =
            "DELETE FROM event_publication WHERE completion_date IS NOT NULL " +
                "AND completion_date < now() - INTERVAL '$RETENTION_DAYS days'"
    }
}
