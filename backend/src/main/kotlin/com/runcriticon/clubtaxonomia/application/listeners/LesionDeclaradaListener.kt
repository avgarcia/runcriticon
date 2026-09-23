package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.StudentClassification
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.ensureAssignable
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.api.events.LesionDeclarada
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
 * (con `before`/`after` completos) sin necesitar ese permiso ni un `Principal` real de sesión — se construye uno ad-hoc con
 * `Role.ALUMNO` y el propio alumno como actor, coherente con que es él quien lo decidió en el modal.
 *
 * **Reemplaza el eje, no añade**: aunque el dominio permite N-M valores por eje (`StudentTags`, "que un
 * alumno pueda tener varios valores del mismo eje es *ausencia* de regla"), el tag `estado` es
 * conceptualmente exclusivo — el wireframe habla de "cambiar" el estado, no de sumarle uno. Se desasigna
 * cualquier otro valor que el alumno tuviera bajo el eje `estado` antes de asignar "lesión".
 *
 * **Fail-closed silencioso si la taxonomía no tiene el eje/valor esperado** (admin lo renombró o archivó):
 * no hay ningún mecanismo de "tag reservado del sistema" en este agregado, así que la búsqueda es por nombre
 * normalizado. Si falla, se loguea y no se muta nada — el aviso al entrenador y la marca de dolor de
 * `RescheduleDayCommand` ya se dispararon con independencia de este listener, así que el alumno no se queda
 * sin ser atendido, solo sin el cambio de estado visible en fichas/grupos.
 */
@Component
class LesionDeclaradaListener(
    private val classification: StudentClassification,
    private val studentTags: StudentTagRepository,
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
        classification
            .classify(actor, PersonId.of(event.aggregateId)) { context ->
                val axis = findEstadoAxis(context.taxonomy)
                if (axis == null) {
                    log.warn(
                        "Club {} no tiene el eje 'estado' o el valor 'lesión' en su taxonomía; no se muta " +
                            "la clasificación del alumno {} tras {}",
                        event.clubId,
                        event.aggregateId,
                        LISTENER,
                    )
                    return@classify
                }
                val (estadoValueIds, lesionValueId) = axis
                ensureAssignable(context, lesionValueId)
                ((context.assigned intersect estadoValueIds) - lesionValueId).forEach {
                    studentTags.remove(context.clubId, context.studentId, it)
                }
                if (lesionValueId !in context.assigned) {
                    studentTags.add(context.clubId, context.studentId, lesionValueId)
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

/** Localiza el eje `estado` y su valor `lesión` por nombre normalizado (sin tildes, minúsculas) — no existe
 * un mecanismo de "tag reservado del sistema" que los identifique por id. `null` si el admin renombró o
 * archivó cualquiera de los dos. */
private fun findEstadoAxis(taxonomy: Taxonomy): Pair<Set<TagValueId>, TagValueId>? =
    taxonomy.keys
        .firstOrNull { it.label.normalized == "estado" }
        ?.let { estadoKey ->
            estadoKey.values
                .firstOrNull { it.label.normalized == "lesion" }
                ?.let { lesionValue -> estadoKey.values.mapTo(mutableSetOf()) { it.id } to lesionValue.id }
        }
