package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ResolvedPace
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionVolume
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.infrastructure.persistence.RESOLVED_SESSION_MAPPER
import com.runcriticon.seguimiento.infrastructure.persistence.ResolvedSessionPayload
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Adaptador de [ResolvedPlanProjection] sobre `JdbcTemplate`. Sin `@Entity`: este módulo va 100 % JDBC, mismo
 * criterio que `planificacion` (`WeeklyPlanRepositoryJdbc`).
 *
 * Una fila por `(alumno, sesión)`: `students.size * sessions.size` sentencias. Un plan con 0 alumnos en el
 * snapshot (grupo vacío al publicar) o 0 sesiones (`WeeklyPlan.publish` ya lo impide) simplemente no escribe
 * nada — no es un error de este listener.
 *
 * Sin `batchUpdate`: como en `WeeklyPlanRepositoryJdbc`, los argumentos de una fila llevan `null` (ritmo,
 * mensaje al alumno) y el `Array<Any?>` resultante no encaja en la sobrecarga `List<Array<out Any>>` de
 * `batchUpdate`. A este volumen (alumnos × sesiones de un plan semanal) el coste de una sentencia por fila es
 * irrelevante.
 */
@Repository
class ResolvedPlanProjectionJdbc(
    private val jdbc: JdbcTemplate,
) : ResolvedPlanProjection {
    /**
     * Escritura dirigida por eventos, no por una petición: corre en el listener del outbox, sin
     * `SecurityContext` ni principal. El `club_id` que se escribe viene del propio evento, publicado por
     * `planificacion`, no de entrada de usuario.
     */
    @NoAuthScope(
        justificacion =
            "Escritura de proyección dirigida por integration events: sin principal en el listener; el " +
                "club_id proviene del evento PlanPublicado, no de entrada de usuario.",
    )
    override fun replacePlan(
        clubId: ClubId,
        planId: PlanId,
        students: Set<StudentId>,
        sessions: List<ResolvedSession>,
        eventId: UUID,
        occurredAt: Instant,
    ) {
        if (students.isEmpty() || sessions.isEmpty()) return

        val timestamp = Timestamp.from(occurredAt)
        students.forEach { student ->
            sessions.forEach { session ->
                jdbc.update(UPSERT_SQL, *rowArgs(student, planId, clubId, session, eventId, timestamp))
            }
        }
    }

    /** Agregado de sistema para el gauge: no devuelve datos de ningún alumno concreto. */
    @NoAuthScope(
        justificacion =
            "Agregado de sistema para el gauge projection_lag_seconds: no devuelve datos de cliente y lo " +
                "invoca Micrometer, sin principal.",
    )
    override fun lagSeconds(): Long = jdbc.queryForObject(LAG_SQL, Long::class.java) ?: 0L
}

private fun rowArgs(
    student: StudentId,
    planId: PlanId,
    clubId: ClubId,
    session: ResolvedSession,
    eventId: UUID,
    timestamp: Timestamp,
): Array<Any?> {
    val payload =
        ResolvedSessionPayload(
            tipo = session.type.name,
            volumenTipo =
                (session.volume as? SessionVolume.Distance)?.let { "DISTANCIA" }
                    ?: (session.volume as? SessionVolume.Duration)?.let { "TIEMPO" },
            volumenMetros = (session.volume as? SessionVolume.Distance)?.meters,
            volumenMinutos = (session.volume as? SessionVolume.Duration)?.minutes,
            notas = session.notes,
        )
    return arrayOf(
        student.value,
        planId.value,
        clubId.value,
        session.day,
        RESOLVED_SESSION_MAPPER.writeValueAsString(payload),
        paceOriginType(session.pace),
        session.pace?.secondsPerKm,
        session.pace?.referenceDistance?.toLiteral(),
        session.pace?.missingMark?.toLiteral(),
        // LAL-29 crea ya estas dos columnas pero nunca las rellena: no hay evento de personalización todavía
        // (llega con LAL-26).
        session.messageToStudent,
        session.isPersonalized,
        eventId,
        timestamp,
    )
}

private fun paceOriginType(pace: ResolvedPace?): String? =
    when {
        pace == null -> null
        pace.secondsPerKm != null -> "ABSOLUTO"
        else -> "RELATIVO"
    }

private fun RaceDistance.toLiteral(): String =
    when (this) {
        RaceDistance.FIVE_K -> "5K"
        RaceDistance.TEN_K -> "10K"
        RaceDistance.HALF_MARATHON -> "21K"
        RaceDistance.MARATHON -> "42K"
    }

// A nivel de fichero, no en `companion object`: un val de companion genera un accesor sintético público que
// AuthorizationArchTest contaría como método del `@Repository` sin `@AuthScope`/`@NoAuthScope`.
private val UPSERT_SQL =
    """
    INSERT INTO seguimiento.plan_resuelto_por_alumno
        (alumno_id, plan_id, club_id, dia, sesion_resuelta, ritmo_tipo_origen, ritmo_calculado_seg_por_km,
         ritmo_referencia_distancia, ritmo_falta_marca, mensaje_al_alumno, es_personalizada,
         last_processed_event_id, last_processed_event_ts)
    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (alumno_id, plan_id, dia) DO UPDATE SET
        club_id                     = EXCLUDED.club_id,
        sesion_resuelta             = EXCLUDED.sesion_resuelta,
        ritmo_tipo_origen           = EXCLUDED.ritmo_tipo_origen,
        ritmo_calculado_seg_por_km  = EXCLUDED.ritmo_calculado_seg_por_km,
        ritmo_referencia_distancia  = EXCLUDED.ritmo_referencia_distancia,
        ritmo_falta_marca           = EXCLUDED.ritmo_falta_marca,
        mensaje_al_alumno           = EXCLUDED.mensaje_al_alumno,
        es_personalizada            = EXCLUDED.es_personalizada,
        last_processed_event_id     = EXCLUDED.last_processed_event_id,
        last_processed_event_ts     = EXCLUDED.last_processed_event_ts
    """.trimIndent()

/** `COALESCE` sobre el máximo: una proyección vacía da lag 0, no un lag infinito que dispararía la alarma. */
private val LAG_SQL =
    """
    SELECT EXTRACT(EPOCH FROM (now() - COALESCE(MAX(last_processed_event_ts), now())))::BIGINT
    FROM seguimiento.plan_resuelto_por_alumno
    """.trimIndent()
