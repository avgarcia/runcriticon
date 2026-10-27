package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.HealthConsentProjection
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.StudentClassification
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.taxonomy.healthDataValueIds
import com.runcriticon.identidad.api.events.ConsentimientoConcedido
import com.runcriticon.identidad.api.events.ConsentimientoRevocado
import com.runcriticon.shared.autorizacion.ListenerPrincipalScope
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Mantiene la proyección local `consentimiento_alumno` a partir de `ConsentimientoConcedido`/`ConsentimientoRevocado`
 * (consentimiento explícito de datos de salud, ADR-0014 D16/D18) y, al revocar, **retira del alumno los valores
 * marcados como dato de salud** (`TagValue.healthData`: `lesión`, `post-parto`).
 *
 * Su única base legal es ese consentimiento: tras revocar, el tratamiento dependiente cesa (ADR-0014 D18), y que esos
 * valores sigan a la vista de todo el club es tratarlos. Se retiran —no se ocultan en lectura— porque la pertenencia a
 * un grupo filtrado por uno de ellos también lo delataría, y quitar el tag recalcula esa pertenencia sola.
 * Consecuencia asumida: al volver a conceder, los valores **no** reaparecen; hay que volver a asignarlos. La proyección
 * es además la que permite a `ensureAssignable` negarse a asignarlos a quien no ha consentido.
 *
 * **Guarda de orden**: conceder y revocar alternan sobre la misma fila y el outbox no garantiza el orden de entrega. La
 * retirada solo se hace si la revocación quedó como el estado vigente ([HealthConsentProjection.upsert] devuelve
 * `true`): una revocación rezagada, tras una concesión posterior, no debe quitar lo que ya corresponde.
 *
 * La retirada pasa por [StudentClassification.classify] —recálculo de grupos y asiento de auditoría `before`/`after`—
 * con el alumno como principal en curso ([ListenerPrincipalScope]), igual que `LesionDeclaradaListener`. Un evento de
 * alguien que no es alumno (un entrenador que revoca) no tiene tags: `classify` devuelve `StudentNotFound` y se ignora.
 */
@Component
class HealthConsentListener(
    private val projection: HealthConsentProjection,
    private val classification: StudentClassification,
    private val studentTags: StudentTagRepository,
    private val principalScope: ListenerPrincipalScope,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de `application`
    // dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("clubTaxonomiaProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: ConsentimientoConcedido) {
        handle(event) { project(event, granted = true) }
    }

    @ApplicationModuleListener
    fun on(event: ConsentimientoRevocado) {
        handle(event) {
            if (project(event, granted = false)) retireHealthData(event)
        }
    }

    private fun project(
        event: IntegrationEvent,
        granted: Boolean,
    ): Boolean =
        projection.upsert(
            clubId = ClubId.of(event.clubId),
            studentId = PersonId.of(event.aggregateId),
            granted = granted,
            eventId = event.eventId,
            occurredAt = event.occurredAt,
        )

    private fun retireHealthData(event: ConsentimientoRevocado) {
        val actor = Principal(userId = event.aggregateId, clubId = event.clubId, role = Role.ALUMNO)
        principalScope.runAs(actor) {
            classification
                .classify(actor, PersonId.of(event.aggregateId)) { context ->
                    (context.assigned intersect context.taxonomy.healthDataValueIds()).forEach {
                        studentTags.remove(context.clubId, context.studentId, it)
                    }
                }.onLeft { error ->
                    log.debug("Sin datos de salud que retirar tras {} para {}: {}", LISTENER, event.aggregateId, error)
                }
        }
    }

    private fun handle(
        event: IntegrationEvent,
        action: () -> Unit,
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
            action()
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val MODULE = "club_taxonomia"
        const val LISTENER = "HealthConsentListener"
    }
}
