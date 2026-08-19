package com.runcriticon.auditoria.infrastructure.persistence.events

import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.events.ProcessedEventTracker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Implementación de [ProcessedEventTracker] contra `auditoria.evento_procesado` — mismo patrón que
 * `ClubTaxonomiaProcessedEventTracker`: la tabla es de este módulo, así que su implementación vive aquí y no en
 * `shared`.
 */
@Repository
@Qualifier(AuditoriaProcessedEventTracker.QUALIFIER)
class AuditoriaProcessedEventTracker(
    private val jdbc: JdbcTemplate,
) : ProcessedEventTracker {
    @NoAuthScope(
        justificacion =
            "Tabla de idempotencia interna del módulo (SIN_PII): no contiene datos de cliente ni club_id, y se " +
                "invoca desde un listener del outbox, sin principal.",
    )
    override fun markIfNew(
        listener: String,
        eventId: UUID,
    ): Boolean = jdbc.update(MARK_SQL, listener, eventId) == 1

    companion object {
        const val QUALIFIER = "auditoriaProcessedEventTracker"
    }
}

private val MARK_SQL =
    """
    INSERT INTO auditoria.evento_procesado (listener, event_id)
    VALUES (?, ?)
    ON CONFLICT (listener, event_id) DO NOTHING
    """.trimIndent()
