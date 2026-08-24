package com.runcriticon.seguimiento.infrastructure.events

import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.events.ProcessedEventTracker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Implementación de [ProcessedEventTracker] contra `seguimiento.evento_procesado`. Calcada de
 * `PlanificacionProcessedEventTracker`/`ClubTaxonomiaProcessedEventTracker` — cada módulo que consume eventos
 * tiene la suya, sin implementación compartida entre esquemas.
 */
@Repository
@Qualifier(SeguimientoProcessedEventTracker.QUALIFIER)
class SeguimientoProcessedEventTracker(
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
        const val QUALIFIER = "seguimientoProcessedEventTracker"
    }
}

// A nivel de fichero, no en `companion object`: un val de companion genera un accesor sintético público que
// AuthorizationArchTest contaría como método del `@Repository` sin `@AuthScope`/`@NoAuthScope`.
private val MARK_SQL =
    """
    INSERT INTO seguimiento.evento_procesado (listener, event_id)
    VALUES (?, ?)
    ON CONFLICT (listener, event_id) DO NOTHING
    """.trimIndent()
