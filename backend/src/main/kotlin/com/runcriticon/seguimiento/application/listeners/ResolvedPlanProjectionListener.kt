package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ResolvedPace
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionType
import com.runcriticon.seguimiento.domain.SessionVolume
import com.runcriticon.seguimiento.domain.StudentId
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
 * **Trampa de literales** (documentada en `PublishPlanCommand.toPublishedSession`): el evento usa
 * `"DISTANCIA"`/`"TIEMPO"` para el volumen, mientras que la columna `planificacion.sesion.volumen_tipo` usa
 * `'DISTANCE'`/`'DURATION'`. Este mapeador consume el **evento**, así que compara contra los literales del
 * evento — usar los de la columna aquí produciría `null`s silenciosos.
 */
@Component
class ResolvedPlanProjectionListener(
    private val projection: ResolvedPlanProjection,
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
            projection.replacePlan(
                clubId = ClubId.of(event.clubId),
                planId = PlanId.of(event.aggregateId),
                students = event.snapshotAlumnos.mapTo(mutableSetOf()) { StudentId.of(it) },
                sessions = event.sesiones.map { it.toResolvedSession() },
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

private fun PublishedSession.toResolvedSession(): ResolvedSession =
    ResolvedSession(
        day = dia,
        type = SessionType.valueOf(tipo),
        volume = toVolume(),
        pace = toPace(),
        notes = notas,
    )

private fun PublishedSession.toVolume(): SessionVolume? =
    when (volumenTipo) {
        "DISTANCIA" -> volumenMetros?.let { SessionVolume.Distance(it) }
        "TIEMPO" -> volumenMinutos?.let { SessionVolume.Duration(it) }
        else -> null
    }

/**
 * Sin marcas del alumno todavía (LAL-31), todo ritmo `RELATIVO` cae en el caso "falta marca": nunca hay
 * [ResolvedPace.secondsPerKm] ni [ResolvedPace.referenceDistance] resueltos por este listener.
 */
private fun PublishedSession.toPace(): ResolvedPace? =
    when (ritmoTipo) {
        "ABSOLUTO" -> ritmoSegundosPorKm?.let { ResolvedPace(secondsPerKm = it) }
        "RELATIVO" -> ritmoReferencia?.toRaceDistance()?.let { ResolvedPace(missingMark = it) }
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
