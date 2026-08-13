package com.runcriticon.planificacion.infrastructure.events

import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.events.ProcessedEventTracker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Implementación de [ProcessedEventTracker] contra `planificacion.evento_procesado`. Calco literal de
 * `ClubTaxonomiaProcessedEventTracker`: la tabla es de cada módulo, así que cada módulo que consuma eventos
 * lleva la suya, con [Qualifier] desde el primer día.
 *
 * **Frontera transaccional**: `markIfNew` corre en la misma transacción que la escritura que protege — la que
 * pone `@ApplicationModuleListener` alrededor del listener. Nunca `REQUIRES_NEW` aquí.
 */
@Repository
@Qualifier(PlanificacionProcessedEventTracker.QUALIFIER)
class PlanificacionProcessedEventTracker(
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
        const val QUALIFIER = "planificacionProcessedEventTracker"
    }
}

private val MARK_SQL =
    """
    INSERT INTO planificacion.evento_procesado (listener, event_id)
    VALUES (?, ?)
    ON CONFLICT (listener, event_id) DO NOTHING
    """.trimIndent()
