package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.AuditTrail
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonErasure
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.identidad.api.events.AdminEliminado
import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Aplica en este módulo el derecho de supresión: cuando el módulo de identidad da de baja a una persona, borra
 * físicamente lo que este módulo guarda de ella —su fila en la proyección, sus asignaciones de tags y sus excepciones
 * manuales de pertenencia a grupos—, anonimiza los asientos de `evento_auditoria` que la mencionan y deja la lápida
 * que impide resucitarla.
 *
 * Cubre **las tres bajas** (alumno, entrenador y, desde LAL-126, admin), porque los tres pueden dejar rastro en este
 * módulo: el alumno y el entrenador tienen proyección propia; el admin no se proyecta nunca (no hay `AdminInvitado`),
 * pero sí puede aparecer como `actor_id` en `evento_auditoria` — clasificar alumnos y gestionar la taxonomía están en
 * su matriz de autorización. El nombre lo fija el patrón obligatorio de supresión que sigue todo módulo con datos
 * personales primarios, de ahí que no se llame `PersonDeletionListener` pese a atender también a entrenador y admin.
 *
 * **Borrado mixto** (ADR-0014 D6): físico para la proyección y las asignaciones (categoría 1), anonimización para
 * `evento_auditoria` (categoría 2) — la fila sobrevive sin `actor_id`/`sujeto_id`, es responsabilidad proactiva, no
 * un olvido. Para la baja de un admin, [PersonErasure.erase] es un no-op seguro (nunca hubo fila de proyección que
 * borrar) y solo [AuditTrail.anonymize] hace trabajo real.
 *
 * Dos protecciones distintas, como en [PersonProjectionListener]: la reentrega del mismo evento la corta el
 * [ProcessedEventTracker]; que un alta rezagada resucite a la persona lo corta la lápida que escribe
 * [PersonErasure.erase].
 */
@Component
class StudentDeletionListener(
    private val personErasure: PersonErasure,
    private val auditTrail: AuditTrail,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de `application`
    // dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("clubTaxonomiaProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Borra todo lo que el club guarda del alumno suprimido. */
    @ApplicationModuleListener
    fun on(event: AlumnoEliminado) = purge(event)

    /** Borra todo lo que el club guarda del entrenador suprimido. */
    @ApplicationModuleListener
    fun on(event: EntrenadorEliminado) = purge(event)

    /**
     * El admin nunca tiene proyección que borrar; lo que sí puede tener son asientos de `evento_auditoria` como
     * `actor_id` (clasificó alumnos o gestionó la taxonomía) — esos son los que esta baja anonimiza (LAL-126).
     */
    @ApplicationModuleListener
    fun on(event: AdminEliminado) = purge(event)

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
            val erased = personErasure.erase(PersonId.of(event.aggregateId))
            val anonymized = auditTrail.anonymize(event.aggregateId)
            // Sin el id de la persona en el log: es justo el dato que se acaba de borrar.
            log.info(
                "Supresión aplicada: {} filas de proyección, {} asignaciones de tag, {} excepciones de grupo, " +
                    "{} asignaciones de entrenador a grupo borradas y {} asientos de auditoría anonimizados",
                erased.projections,
                erased.tagAssignments,
                erased.groupOverrides,
                erased.groupCoachAssignments,
                anonymized,
            )
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        /** Tag `module` del MDC: el esquema SQL del módulo, no su paquete Kotlin. */
        const val MODULE = "club_taxonomia"

        /** Clave de idempotencia propia, distinta de la del listener de proyección. */
        const val LISTENER = "StudentDeletionListener"
    }
}
