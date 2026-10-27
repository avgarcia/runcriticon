package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.StudentClassification
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.ensureAssignable
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.taxonomy.injuryAxis
import com.runcriticon.shared.api.events.LesionDeclarada
import com.runcriticon.shared.autorizacion.ListenerPrincipalScope
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Muta el tag `estado` del alumno a "lesión" cuando confirma el cambio desde el reajuste de día que avisa de lesión
 * (wireframe 07 §Flujo B opción 4).
 *
 * **Reutiliza [StudentClassification.classify] directamente, no `AssignStudentTagCommand`**: ese caso de uso
 * exige `STUDENT:CLASSIFY`, permiso que `ALUMNO` no tiene sobre sí mismo — la clasificación es cosa de
 * `ENTRENADOR`/`ADMIN` en el flujo normal. `classify` es el colaborador compartido que no comprueba la matriz
 * (esa comprobación vive en cada `@ApplicationService`, nunca aquí), así que llamarlo desde un listener
 * evento-driven da gratis el recálculo de membresía de grupos y el asiento de auditoría `before`/`after`
 * (con `before`/`after` completos) sin necesitar ese permiso ni un `Principal` real de sesión — se construye uno
 * ad-hoc con
 * `Role.ALUMNO` y el propio alumno como actor, coherente con que es él quien lo decidió en el modal.
 *
 * **Ese principal tiene que ir al contexto de seguridad, no solo como argumento**: `classify` usa repositorios con
 * `@AuthScope(Scope.CLUB)` y el listener corre sin sesión, así que sin [ListenerPrincipalScope] el aspecto fallaba
 * cerrado en cada evento — el tag no se asignaba nunca y la publicación quedaba sin completar en el outbox.
 *
 * **Reemplaza el eje, no añade**: aunque el dominio permite N-M valores por eje (`StudentTags`, "que un
 * alumno pueda tener varios valores del mismo eje es *ausencia* de regla"), el tag `estado` es
 * conceptualmente exclusivo — el wireframe habla de "cambiar" el estado, no de sumarle uno. Se desasigna
 * cualquier otro valor que el alumno tuviera bajo el eje `estado` antes de asignar "lesión".
 *
 * **El valor se reconoce por su papel reservado** ([com.runcriticon.clubtaxonomia.domain.tag.ReservedRole.LESION]),
 * no por su nombre: renombrar el valor o el eje no rompe nada. **Fail-closed** si el club no tiene ese valor o si el
 * admin lo archivó (deja de ser asignable): se loguea y no se muta nada — el aviso al entrenador y la marca de dolor
 * de `RescheduleDayCommand` ya se dispararon con independencia de este listener, así que el alumno no se queda sin
 * ser atendido, solo sin el cambio de estado visible en fichas/grupos. Lo mismo si el alumno no tiene consentimiento
 * vigente de datos de salud (ver `ensureAssignable`).
 */
@Component
class LesionDeclaradaListener(
    private val classification: StudentClassification,
    private val studentTags: StudentTagRepository,
    private val principalScope: ListenerPrincipalScope,
    // Qualifier por el literal, no por la constante del adaptador (mismo motivo que PersonProjectionListener):
    // importarla obligaría a esta clase de `application` a depender de `infrastructure`, dirección prohibida
    // que verifica CapasArchTest.
    @Qualifier("clubTaxonomiaProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: LesionDeclarada) {
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
            mutateEstadoTag(event)
        } finally {
            mdcRestorer.clear()
        }
    }

    private fun mutateEstadoTag(event: LesionDeclarada) {
        val actor = Principal(userId = event.aggregateId, clubId = event.clubId, role = Role.ALUMNO)
        principalScope.runAs(actor) { classifyAsInjured(actor, event) }
    }

    private fun classifyAsInjured(
        actor: Principal,
        event: LesionDeclarada,
    ) {
        classification
            .classify(actor, PersonId.of(event.aggregateId)) { context ->
                val axis = context.taxonomy.injuryAxis()
                if (axis == null) {
                    log.warn(
                        "Club {} no tiene ningún valor con papel LESION en su taxonomía; no se muta " +
                            "la clasificación del alumno {} tras {}",
                        event.clubId,
                        event.aggregateId,
                        LISTENER,
                    )
                    return@classify
                }
                val (estadoValueIds, injuryValueId) = axis
                ensureAssignable(context, injuryValueId)
                ((context.assigned intersect estadoValueIds) - injuryValueId).forEach {
                    studentTags.remove(context.clubId, context.studentId, it)
                }
                if (injuryValueId !in context.assigned) {
                    studentTags.add(context.clubId, context.studentId, injuryValueId)
                }
            }.onLeft { error ->
                log.warn("No se pudo mutar el tag estado a 'lesión' para el alumno {}: {}", event.aggregateId, error)
            }
    }

    private companion object {
        const val MODULE = "club_taxonomia"
        const val LISTENER = "LesionDeclaradaListener"
    }
}
