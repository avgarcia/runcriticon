package com.runcriticon.planificacion.application.listeners

import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.planificacion.application.ports.outbound.persistence.PlanificacionErasure
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Aplica en este módulo el derecho de supresión: cuando `identidad` da de baja a una persona, borra físicamente
 * lo que este módulo guarda de ella. Mismo patrón que `StudentDeletionListener` de `club_taxonomia` — nombre
 * distinto porque aquí sí cubre a ambos roles con el mismo peso (borra el plan entero del entrenador, no solo una
 * fila de relación).
 *
 * Este módulo no tiene tablas de auditoría propias, así que el borrado es puramente físico, igual que en
 * `club_taxonomia`.
 */
@Component
class PlanificacionDeletionListener(
    private val erasure: PlanificacionErasure,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de `application`
    // dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("planificacionProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Borra todo lo que este módulo guarda del alumno suprimido. */
    @ApplicationModuleListener
    fun on(event: AlumnoEliminado) = purge(event)

    /** Borra todo lo que este módulo guarda del entrenador suprimido, incluidos sus planes. */
    @ApplicationModuleListener
    fun on(event: EntrenadorEliminado) = purge(event)

    private fun purge(event: IntegrationEvent) {
        mdcRestorer.restore(
            module = MODULE,
            traceparent = event.traceparent,
            clubId = event.clubId,
            actorId = event.actorId,
        )
        try {
            if (!processedEvents.markIfNew(LISTENER, event.eventId)) {
                log.debug("Evento {} ya procesado por {}; se descarta", event.eventId, LISTENER)
                return
            }
            val erased = erasure.erase(PersonId.of(event.aggregateId))
            // Sin el id de la persona en el log: es justo el dato que se acaba de borrar.
            log.info(
                "Supresión aplicada: {} planes, {} personalizaciones, {} entradas de snapshot y {} " +
                    "pertenencias a grupo borradas",
                erased.plans,
                erased.personalizations,
                erased.snapshotEntries,
                erased.groupMemberships,
            )
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val MODULE = "planificacion"
        const val LISTENER = "PlanificacionDeletionListener"
    }
}
