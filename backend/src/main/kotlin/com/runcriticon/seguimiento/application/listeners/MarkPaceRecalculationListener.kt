package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.seguimiento.api.events.MarcaActualizada
import com.runcriticon.seguimiento.api.events.MarcaRetirada
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.application.ports.outbound.persistence.StudentMarkLookup
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Recalcula `plan_resuelto_por_alumno` cuando el alumno registra, edita o retira una marca (LAL-32,
 * ADR-0002 D8): consume `MarcaActualizada`/`MarcaRetirada`, publicados por este mismo módulo — el primer
 * listener del repo que consume un `IntegrationEvent` propio, no de otro bounded context. Antes de este
 * listener, ambos eventos no tenían consumidor y nunca llegaban a crear filas en `event_publication`.
 *
 * **No confía en el payload del evento**: en vez de usar `MarcaActualizada.tiempoSegundos`, vuelve a leer la
 * marca actual con [StudentMarkLookup.findMark] y recalcula contra ese valor. Así, si dos ediciones de la
 * misma marca se entregan fuera de orden, el resultado converge siempre al estado real — no hace falta
 * guarda de orden por `occurredAt` (a diferencia de `PersonalizationProjectionListener`), solo idempotencia
 * frente a reentregas del mismo evento.
 *
 * `MarcaActualizada` y `MarcaRetirada` comparten el mismo camino: ambos terminan preguntando "¿qué marca
 * tiene ahora mismo el alumno en esta distancia?" — la respuesta (`StudentMark?`) decide si
 * [ResolvedPlanProjection.recalculateRelativePaces] resuelve la fila o la deja en "falta marca".
 */
@Component
class MarkPaceRecalculationListener(
    private val projection: ResolvedPlanProjection,
    private val marks: StudentMarkLookup,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de
    // `application` dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("seguimientoProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: MarcaActualizada) = recalculate(event, event.distancia)

    @ApplicationModuleListener
    fun on(event: MarcaRetirada) = recalculate(event, event.distancia)

    private fun recalculate(
        event: IntegrationEvent,
        distanciaLiteral: String,
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
            val distance = distanciaLiteral.toRaceDistance() ?: return
            val clubId = ClubId.of(event.clubId)
            // aggregateId de MarcaActualizada/MarcaRetirada es el alumno (ver su KDoc).
            val studentId = StudentId.of(event.aggregateId)
            val mark = marks.findMark(clubId, studentId, distance)
            val rows = projection.recalculateRelativePaces(clubId, studentId, distance, mark?.paceSecondsPerKm())
            log.debug(
                "Recalculadas {} filas de ritmo relativo para alumno {} en distancia {}",
                rows,
                studentId,
                distance,
            )
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val MODULE = "seguimiento"
        const val LISTENER = "MarkPaceRecalculationListener"
    }
}

private fun String.toRaceDistance(): RaceDistance? =
    when (this) {
        "5K" -> RaceDistance.FIVE_K
        "10K" -> RaceDistance.TEN_K
        "21K" -> RaceDistance.HALF_MARATHON
        "42K" -> RaceDistance.MARATHON
        else -> null
    }
