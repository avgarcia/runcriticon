package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.planificacion.api.events.PersonalizacionAplicada
import com.runcriticon.planificacion.api.events.PersonalizacionRetirada
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ResolvedPace
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionType
import com.runcriticon.seguimiento.domain.SessionVolume
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
 * Mantiene `plan_resuelto_por_alumno` sincronizada con las personalizaciones de un plan ya publicado
 * (LAL-26): `PersonalizacionAplicada` sustituye la sesión resuelta de un alumno por su override,
 * `PersonalizacionRetirada` la devuelve a la sesión base.
 *
 * A diferencia de `ResolvedPlanProjectionListener` (`PlanPublicado` es terminal, sin guarda de orden), aquí
 * **sí hace falta guarda de orden por `occurredAt`** — mismo criterio que `ConsentProjectionListener`:
 * aplicar y retirar alternan sobre la misma fila, y el outbox no garantiza el orden de entrega entre eventos
 * de agregados distintos (ni siquiera del mismo).
 *
 * Ambos eventos delegan en el mismo [ResolvedPlanProjection.writePersonalizedSession]: solo cambia qué
 * [ResolvedSession] construye cada `on(...)` — el override con `isPersonalized = true`, la base con
 * `isPersonalized = false` y sin mensaje. Mismo criterio que `ConsentProjectionListener.apply(granted)`.
 */
@Component
class PersonalizationProjectionListener(
    private val projection: ResolvedPlanProjection,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de
    // `application` dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("seguimientoProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: PersonalizacionAplicada) =
        apply(
            event = event,
            studentId = StudentId.of(event.alumnoId),
            session =
                ResolvedSession(
                    day = event.dia,
                    planId = PlanId.of(event.aggregateId),
                    type = SessionType.valueOf(event.override.tipo),
                    volume = event.override.toVolume(),
                    pace = event.override.toPace(),
                    notes = event.override.notas,
                    messageToStudent = event.mensajeAlAlumno,
                    isPersonalized = true,
                ),
        )

    @ApplicationModuleListener
    fun on(event: PersonalizacionRetirada) =
        apply(
            event = event,
            studentId = StudentId.of(event.alumnoId),
            session =
                ResolvedSession(
                    day = event.dia,
                    planId = PlanId.of(event.aggregateId),
                    type = SessionType.valueOf(event.baseSession.tipo),
                    volume = event.baseSession.toVolume(),
                    pace = event.baseSession.toPace(),
                    notes = event.baseSession.notas,
                    messageToStudent = null,
                    isPersonalized = false,
                ),
        )

    private fun apply(
        event: IntegrationEvent,
        studentId: StudentId,
        session: ResolvedSession,
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
            projection.writePersonalizedSession(
                clubId = ClubId.of(event.clubId),
                studentId = studentId,
                session = session,
                eventId = event.eventId,
                occurredAt = event.occurredAt,
            )
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val MODULE = "seguimiento"
        const val LISTENER = "PersonalizationProjectionListener"
    }
}

private fun PersonalizedSession.toVolume(): SessionVolume? =
    when (volumenTipo) {
        "DISTANCIA" -> volumenMetros?.let { SessionVolume.Distance(it) }
        "TIEMPO" -> volumenMinutos?.let { SessionVolume.Duration(it) }
        else -> null
    }

/**
 * Mismo criterio que `ResolvedPlanProjectionListener.PublishedSession.toPace` — sin marcas del alumno
 * todavía (LAL-31), todo ritmo `RELATIVO` cae en "falta marca", nunca resuelto por este listener.
 */
private fun PersonalizedSession.toPace(): ResolvedPace? =
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
