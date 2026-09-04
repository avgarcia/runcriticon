package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.seguimiento.application.ports.outbound.persistence.SeguimientoErasure
import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Aplica en este módulo el derecho de supresión: cuando `identidad` da de baja a un alumno o a un
 * entrenador, borra físicamente lo que este módulo guarda de él. Mismo patrón que
 * `PlanificacionDeletionListener`/`StudentDeletionListener`.
 *
 * `EntrenadorEliminado` llegó con LAL-116: hasta entonces este módulo no proyectaba nada de entrenadores (no
 * había `entrenador_id` en ninguna tabla); con `grupo_entrenador` (`CoachGroupProjectionListener`) sí, así
 * que hace falta purgarlo igual que `planificacion` purga sus propios planes.
 */
@Component
class SeguimientoDeletionListener(
    private val erasure: SeguimientoErasure,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de
    // `application` dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("seguimientoProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: AlumnoEliminado) =
        purge(event) {
            val erased = erasure.erase(StudentId.of(event.aggregateId))
            // Sin el id del alumno en el log: es justo el dato que se acaba de borrar.
            log.info(
                "Supresión aplicada: {} filas de plan resuelto, {} reportes de sesión y {} filas de " +
                    "consentimiento borradas",
                erased.resolvedSessions,
                erased.sessionReports,
                erased.consentRows,
            )
        }

    @ApplicationModuleListener
    fun on(event: EntrenadorEliminado) =
        purge(event) {
            val erased = erasure.eraseCoach(CoachId.of(event.aggregateId))
            log.info("Supresión aplicada: {} filas de grupo_entrenador borradas", erased)
        }

    private fun purge(
        event: IntegrationEvent,
        apply: () -> Unit,
    ) {
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
            apply()
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val MODULE = "seguimiento"
        const val LISTENER = "SeguimientoDeletionListener"
    }
}
