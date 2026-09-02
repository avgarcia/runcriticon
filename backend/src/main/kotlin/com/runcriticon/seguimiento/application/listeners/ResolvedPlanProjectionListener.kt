package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.application.ports.outbound.persistence.StudentMarkLookup
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ResolvedPace
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionType
import com.runcriticon.seguimiento.domain.SessionVolume
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.seguimiento.domain.resolveRelativePace
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Mantiene la proyección local `plan_resuelto_por_alumno` a partir de `PlanPublicado` (LAL-25). Es el primer
 * consumidor de ese evento y arranca el módulo `seguimiento` (LAL-29): antes de este listener no existía forma
 * de que el alumno viera un plan publicado.
 *
 * Un plan publicado no vuelve a mutar (`WeeklyPlan.publish` es terminal, LAL-25), así que a diferencia de
 * `GroupMembersProjectionListener` no hace falta guarda de orden por `occurredAt` — solo idempotencia frente a
 * reentregas del outbox, que corta [ProcessedEventTracker] por `event_id`.
 *
 * **Ritmo relativo (LAL-32)**: una sola lectura de [StudentMarkLookup.findMarks] para todo el snapshot (evita
 * el N+1), y cada sesión `RELATIVO` se resuelve contra la marca **del alumno al que se está escribiendo esa
 * fila** — dos alumnos del mismo plan pueden acabar con un `ritmo_calculado_seg_por_km` distinto para la
 * misma sesión, por eso `ResolvedPlanProjection.replacePlan` recibe un mapa por alumno, no una lista
 * compartida.
 *
 * **Trampa de literales** (documentada en `PublishPlanCommand.toPublishedSession`): el evento usa
 * `"DISTANCIA"`/`"TIEMPO"` para el volumen, mientras que la columna `planificacion.sesion.volumen_tipo` usa
 * `'DISTANCE'`/`'DURATION'`. Este mapeador consume el **evento**, así que compara contra los literales del
 * evento — usar los de la columna aquí produciría `null`s silenciosos.
 */
@Component
class ResolvedPlanProjectionListener(
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
    fun on(event: PlanPublicado) {
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
            val planId = PlanId.of(event.aggregateId)
            val clubId = ClubId.of(event.clubId)
            val students = event.snapshotAlumnos.mapTo(mutableSetOf()) { StudentId.of(it) }
            val marksByStudent = marks.findMarks(clubId, students)
            projection.replacePlan(
                clubId = clubId,
                planId = planId,
                sessionsByStudent =
                    students.associateWith { student ->
                        val studentMarks = marksByStudent[student].orEmpty()
                        event.sesiones.map { it.toResolvedSession(planId, studentMarks) }
                    },
                eventId = event.eventId,
                occurredAt = event.occurredAt,
            )
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val MODULE = "seguimiento"
        const val LISTENER = "ResolvedPlanProjectionListener"
    }
}

private fun PublishedSession.toResolvedSession(
    planId: PlanId,
    marksByDistance: Map<RaceDistance, StudentMark>,
): ResolvedSession =
    ResolvedSession(
        day = dia,
        planId = planId,
        type = SessionType.valueOf(tipo),
        volume = toVolume(),
        pace = toPace(marksByDistance),
        notes = notas,
    )

private fun PublishedSession.toVolume(): SessionVolume? =
    when (volumenTipo) {
        "DISTANCIA" -> volumenMetros?.let { SessionVolume.Distance(it) }
        "TIEMPO" -> volumenMinutos?.let { SessionVolume.Duration(it) }
        else -> null
    }

/**
 * `ABSOLUTO` se copia tal cual. `RELATIVO` se resuelve contra [marksByDistance] con [resolveRelativePace]
 * (LAL-32): si el alumno no tiene la marca de la referencia, el resultado queda sin `secondsPerKm` (empty
 * state). Sin `ritmoDeltaSegundosPorKm` en el evento (no debería ocurrir — `Pace.Relativo` siempre lo lleva,
 * ver `planificacion.domain.Pace`) la fila queda "sin resolver" en vez de asumir un delta de `0`, que
 * fingiría un ritmo igual al de la marca sin que el entrenador lo pidiera.
 */
private fun PublishedSession.toPace(marksByDistance: Map<RaceDistance, StudentMark>): ResolvedPace? =
    when (ritmoTipo) {
        "ABSOLUTO" -> ritmoSegundosPorKm?.let { ResolvedPace.Absolute(it) }
        "RELATIVO" ->
            ritmoReferencia?.toRaceDistance()?.let { reference ->
                val delta = ritmoDeltaSegundosPorKm
                if (delta != null) {
                    resolveRelativePace(reference, delta, marksByDistance[reference])
                } else {
                    ResolvedPace.Relative(reference = reference, deltaSecondsPerKm = null, secondsPerKm = null)
                }
            }
        else -> null
    }

private fun String.toRaceDistance(): RaceDistance? =
    when (this) {
        "5K" -> RaceDistance.FIVE_K
        "10K" -> RaceDistance.TEN_K
        "21K" -> RaceDistance.HALF_MARATHON
        "42K" -> RaceDistance.MARATHON
        else -> null
    }
