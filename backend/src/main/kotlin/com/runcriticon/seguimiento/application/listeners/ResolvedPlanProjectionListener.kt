package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.planificacion.api.PublishedPersonalization
import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.application.ports.outbound.persistence.StudentMarkLookup
import com.runcriticon.seguimiento.domain.GroupId
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
import java.time.LocalDate

/**
 * Mantiene la proyección local `plan_resuelto_por_alumno` a partir de `PlanPublicado` (publicar un plan
 * semanal a un grupo). Es el primer consumidor de ese evento y arranca el módulo `seguimiento` (la vista
 * "hoy" del alumno): antes de este listener no existía forma de que el alumno viera un plan publicado.
 *
 * Un plan publicado no vuelve a mutar (`WeeklyPlan.publish` es terminal), así que a diferencia de
 * `GroupMembersProjectionListener` no hace falta guarda de orden por `occurredAt` — solo idempotencia frente a
 * reentregas del outbox, que corta [ProcessedEventTracker] por `event_id`.
 *
 * **Ritmo relativo**: una sola lectura de [StudentMarkLookup.findMarks] para todo el snapshot (evita
 * el N+1), y cada sesión `RELATIVO` se resuelve contra la marca **del alumno al que se está escribiendo esa
 * fila** — dos alumnos del mismo plan pueden acabar con un `ritmo_calculado_seg_por_km` distinto para la
 * misma sesión, por eso `ResolvedPlanProjection.replacePlan` recibe un mapa por alumno, no una lista
 * compartida.
 *
 * **Trampa de literales** (documentada en `PublishPlanCommand.toPublishedSession`): el evento usa
 * `"DISTANCIA"`/`"TIEMPO"` para el volumen, mientras que la columna `planificacion.sesion.volumen_tipo` usa
 * `'DISTANCE'`/`'DURATION'`. Este mapeador consume el **evento**, así que compara contra los literales del
 * evento — usar los de la columna aquí produciría `null`s silenciosos.
 *
 * **Personalizaciones previas a publicar** (`event.personalizaciones`): las hechas sobre el `BORRADOR` no
 * emiten `PersonalizacionAplicada` (aún no hay snapshot al que proyectar) y solo llegan aquí, embebidas en el
 * evento. Si este listener las ignorase, el alumno vería la sesión base para siempre: el evento se procesa con
 * éxito, así que ninguna reentrega del outbox lo repararía. Se emparejan por `(alumno, día)`, la misma clave
 * de la proyección (`alumno_id, plan_id, dia`) — `PublishedSession` no lleva id de sesión propio.
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
            val personalizationsByStudent = event.personalizaciones.byStudentAndDay()
            projection.replacePlan(
                clubId = clubId,
                planId = planId,
                groupId = GroupId.of(event.grupoId),
                sessionsByStudent =
                    students.associateWith { student ->
                        resolveSessions(
                            sessions = event.sesiones,
                            planId = planId,
                            marksByDistance = marksByStudent[student].orEmpty(),
                            personalizationsByDay = personalizationsByStudent[student].orEmpty(),
                        )
                    },
                eventId = event.eventId,
                occurredAt = event.occurredAt,
            )
        } finally {
            mdcRestorer.clear()
        }
    }

    private fun resolveSessions(
        sessions: List<PublishedSession>,
        planId: PlanId,
        marksByDistance: Map<RaceDistance, StudentMark>,
        personalizationsByDay: Map<LocalDate, PublishedPersonalization>,
    ): List<ResolvedSession> =
        sessions.map { session ->
            personalizationsByDay[session.dia]?.toResolvedSession(planId, marksByDistance)
                ?: session.toResolvedSession(planId, marksByDistance)
        }

    private companion object {
        const val MODULE = "seguimiento"
        const val LISTENER = "ResolvedPlanProjectionListener"
    }
}

private fun List<PublishedPersonalization>.byStudentAndDay(): Map<StudentId, Map<LocalDate, PublishedPersonalization>> =
    groupBy { StudentId.of(it.alumnoId) }.mapValues { (_, forStudent) -> forStudent.associateBy { it.dia } }

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

/**
 * El override pasa por el mismo mapeador que la sesión base — un ritmo relativo personalizado se resuelve
 * contra las marcas del alumno exactamente igual — y solo se le añade lo propio de una personalización.
 */
private fun PublishedPersonalization.toResolvedSession(
    planId: PlanId,
    marksByDistance: Map<RaceDistance, StudentMark>,
): ResolvedSession =
    override
        .asSessionOn(dia)
        .toResolvedSession(planId, marksByDistance)
        .copy(messageToStudent = mensajeAlAlumno, isPersonalized = true)

/** Mismos campos que [PublishedSession] salvo `dia`: el override no tiene fecha propia, la fija su sesión. */
private fun PersonalizedSession.asSessionOn(day: LocalDate) =
    PublishedSession(
        dia = day,
        tipo = tipo,
        volumenTipo = volumenTipo,
        volumenMetros = volumenMetros,
        volumenMinutos = volumenMinutos,
        ritmoTipo = ritmoTipo,
        ritmoSegundosPorKm = ritmoSegundosPorKm,
        ritmoReferencia = ritmoReferencia,
        ritmoDeltaSegundosPorKm = ritmoDeltaSegundosPorKm,
        notas = notas,
    )

private fun PublishedSession.toVolume(): SessionVolume? =
    when (volumenTipo) {
        "DISTANCIA" -> volumenMetros?.let { SessionVolume.Distance(it) }
        "TIEMPO" -> volumenMinutos?.let { SessionVolume.Duration(it) }
        else -> null
    }

/**
 * `ABSOLUTO` se copia tal cual. `RELATIVO` se resuelve contra [marksByDistance] con [resolveRelativePace]
 * (ritmos resueltos por alumno): si el alumno no tiene la marca de la referencia, el resultado queda sin `secondsPerKm`
 * (empty
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
