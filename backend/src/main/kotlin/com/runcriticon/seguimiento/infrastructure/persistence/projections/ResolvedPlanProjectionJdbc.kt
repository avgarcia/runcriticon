package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.domain.GroupId
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
 * Una fila por `(alumno, sesión)`: la suma de sesiones de todos los alumnos de [sessionsByStudent]. Un plan
 * con 0 alumnos en el snapshot (grupo vacío al publicar) o 0 sesiones (`WeeklyPlan.publish` ya lo impide)
 * simplemente no escribe nada — no es un error de este listener.
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
        groupId: GroupId,
        sessionsByStudent: Map<StudentId, List<ResolvedSession>>,
        eventId: UUID,
        occurredAt: Instant,
    ) {
        if (sessionsByStudent.isEmpty()) return

        val timestamp = Timestamp.from(occurredAt)
        sessionsByStudent.forEach { (student, sessions) ->
            sessions.forEach { session ->
                jdbc.update(UPSERT_SQL, *rowArgs(student, planId, groupId, clubId, session, eventId, timestamp))
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

    @NoAuthScope(
        justificacion =
            "Escritura de proyección dirigida por integration events de planificacion (PersonalizacionAplicada/" +
                "Retirada): sin principal en el listener; el club_id proviene del evento, no de entrada de usuario.",
    )
    override fun writePersonalizedSession(
        clubId: ClubId,
        studentId: StudentId,
        session: ResolvedSession,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean {
        val timestamp = Timestamp.from(occurredAt)
        val rowsUpdated =
            jdbc.update(
                UPDATE_PERSONALIZED_SESSION_SQL,
                *personalizedSessionArgs(session, eventId, timestamp),
                studentId.value,
                session.planId.value,
                session.day,
                timestamp,
            )
        return rowsUpdated > 0
    }

    @NoAuthScope(
        justificacion =
            "Escritura de proyección dirigida por integration events del propio módulo (MarcaActualizada/" +
                "MarcaRetirada): sin principal en el listener; el club_id proviene del evento, no de entrada " +
                "de usuario.",
    )
    override fun recalculateRelativePaces(
        clubId: ClubId,
        studentId: StudentId,
        distance: RaceDistance,
        markPaceSecondsPerKm: Int?,
    ): Int {
        val distanceLiteral = distance.toLiteral()
        return if (markPaceSecondsPerKm != null) {
            jdbc.update(
                RESOLVE_RELATIVE_PACE_SQL,
                markPaceSecondsPerKm,
                distanceLiteral,
                clubId.value,
                studentId.value,
                distanceLiteral,
            )
        } else {
            jdbc.update(
                CLEAR_RELATIVE_PACE_SQL,
                distanceLiteral,
                clubId.value,
                studentId.value,
                distanceLiteral,
            )
        }
    }
}

private fun rowArgs(
    student: StudentId,
    planId: PlanId,
    groupId: GroupId,
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
        groupId.value,
        session.day,
        RESOLVED_SESSION_MAPPER.writeValueAsString(payload),
        session.pace.originLiteral(),
        session.pace.secondsPerKmOrNull(),
        session.pace.referenceDistanceOrNull()?.toLiteral(),
        session.pace.missingMarkOrNull()?.toLiteral(),
        session.pace.deltaOrNull(),
        session.messageToStudent,
        session.isPersonalized,
        eventId,
        timestamp,
    )
}

/** El `SET` de [UPDATE_PERSONALIZED_SESSION_SQL] — mismos campos que [rowArgs] salvo `student`/`planId`/`clubId`/
 * `day`, que van en el `WHERE` de esa sentencia, no aquí (LAL-26). */
private fun personalizedSessionArgs(
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
        RESOLVED_SESSION_MAPPER.writeValueAsString(payload),
        session.pace.originLiteral(),
        session.pace.secondsPerKmOrNull(),
        session.pace.referenceDistanceOrNull()?.toLiteral(),
        session.pace.missingMarkOrNull()?.toLiteral(),
        session.pace.deltaOrNull(),
        session.messageToStudent,
        session.isPersonalized,
        eventId,
        timestamp,
    )
}

private fun ResolvedPace?.originLiteral(): String? =
    when (this) {
        null -> null
        is ResolvedPace.Absolute -> "ABSOLUTO"
        is ResolvedPace.Relative -> "RELATIVO"
    }

private fun ResolvedPace?.secondsPerKmOrNull(): Int? =
    when (this) {
        is ResolvedPace.Absolute -> secondsPerKm
        is ResolvedPace.Relative -> secondsPerKm
        null -> null
    }

/** Contexto para el alumno ("basado en tu 10K"): solo cuando el relativo ya resolvió. */
private fun ResolvedPace?.referenceDistanceOrNull(): RaceDistance? =
    (this as? ResolvedPace.Relative)?.takeIf { it.secondsPerKm != null }?.reference

/** Dispara el empty state: solo cuando el relativo todavía no resolvió. */
private fun ResolvedPace?.missingMarkOrNull(): RaceDistance? =
    (this as? ResolvedPace.Relative)?.takeIf { it.secondsPerKm == null }?.reference

private fun ResolvedPace?.deltaOrNull(): Int? = (this as? ResolvedPace.Relative)?.deltaSecondsPerKm

private fun RaceDistance.toLiteral(): String =
    when (this) {
        RaceDistance.FIVE_K -> "5K"
        RaceDistance.TEN_K -> "10K"
        RaceDistance.HALF_MARATHON -> "21K"
        RaceDistance.MARATHON -> "42K"
    }

// A nivel de fichero, no en `companion object`: un val de companion genera un accesor sintético público que
// AuthorizationArchTest contaría como método sin `@AuthScope`/`@NoAuthScope`.
private val UPSERT_SQL =
    """
    INSERT INTO seguimiento.plan_resuelto_por_alumno
        (alumno_id, plan_id, club_id, grupo_id, dia, sesion_resuelta, ritmo_tipo_origen, ritmo_calculado_seg_por_km,
         ritmo_referencia_distancia, ritmo_falta_marca, ritmo_delta_seg_por_km, mensaje_al_alumno,
         es_personalizada, last_processed_event_id, last_processed_event_ts)
    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (alumno_id, plan_id, dia) DO UPDATE SET
        club_id                     = EXCLUDED.club_id,
        grupo_id                    = EXCLUDED.grupo_id,
        sesion_resuelta             = EXCLUDED.sesion_resuelta,
        ritmo_tipo_origen           = EXCLUDED.ritmo_tipo_origen,
        ritmo_calculado_seg_por_km  = EXCLUDED.ritmo_calculado_seg_por_km,
        ritmo_referencia_distancia  = EXCLUDED.ritmo_referencia_distancia,
        ritmo_falta_marca           = EXCLUDED.ritmo_falta_marca,
        ritmo_delta_seg_por_km      = EXCLUDED.ritmo_delta_seg_por_km,
        mensaje_al_alumno           = EXCLUDED.mensaje_al_alumno,
        es_personalizada            = EXCLUDED.es_personalizada,
        last_processed_event_id     = EXCLUDED.last_processed_event_id,
        last_processed_event_ts     = EXCLUDED.last_processed_event_ts
    """.trimIndent()

/**
 * `UPDATE`-only con guarda de orden (LAL-26): ver KDoc de `writePersonalizedSession`. Sin `club_id` en el
 * `SET` — no cambia entre aplicar/retirar una personalización, la fila ya lo tenía desde `replacePlan`.
 */
private val UPDATE_PERSONALIZED_SESSION_SQL =
    """
    UPDATE seguimiento.plan_resuelto_por_alumno
    SET sesion_resuelta             = ?::jsonb,
        ritmo_tipo_origen           = ?,
        ritmo_calculado_seg_por_km  = ?,
        ritmo_referencia_distancia  = ?,
        ritmo_falta_marca           = ?,
        ritmo_delta_seg_por_km      = ?,
        mensaje_al_alumno           = ?,
        es_personalizada            = ?,
        last_processed_event_id     = ?,
        last_processed_event_ts     = ?
    WHERE alumno_id = ? AND plan_id = ? AND dia = ? AND last_processed_event_ts <= ?
    """.trimIndent()

/**
 * Recálculo por marca nueva (LAL-32): `GREATEST(1, ? + ritmo_delta_seg_por_km)` — mismo suelo que
 * `resolveRelativePace`, aquí repetido porque la suma se hace en SQL (varía por fila, el resto de la
 * sentencia no). Exige `ritmo_delta_seg_por_km IS NOT NULL`: una fila legacy (proyectada antes de LAL-32, sin
 * delta) no tiene con qué resolver — se queda en "falta marca" hasta que se republique el plan.
 *
 * `COALESCE(ritmo_referencia_distancia, ritmo_falta_marca) = ?` localiza las filas de esa distancia sin
 * importar si ya estaban resueltas o en empty state — un solo `UPDATE` cubre ambos casos de partida.
 */
private val RESOLVE_RELATIVE_PACE_SQL =
    """
    UPDATE seguimiento.plan_resuelto_por_alumno
    SET ritmo_calculado_seg_por_km = GREATEST(1, ? + ritmo_delta_seg_por_km),
        ritmo_referencia_distancia = ?,
        ritmo_falta_marca          = NULL
    WHERE club_id = ? AND alumno_id = ? AND ritmo_tipo_origen = 'RELATIVO'
      AND ritmo_delta_seg_por_km IS NOT NULL
      AND COALESCE(ritmo_referencia_distancia, ritmo_falta_marca) = ?
    """.trimIndent()

/** Retirada de marca (LAL-32): sin la guarda `ritmo_delta_seg_por_km IS NOT NULL` de [RESOLVE_RELATIVE_PACE_SQL]
 * — también alcanza a las filas legacy, que ya estaban en "falta marca" y con esto quedan igual (no-op real). */
private val CLEAR_RELATIVE_PACE_SQL =
    """
    UPDATE seguimiento.plan_resuelto_por_alumno
    SET ritmo_calculado_seg_por_km = NULL,
        ritmo_referencia_distancia = NULL,
        ritmo_falta_marca          = ?
    WHERE club_id = ? AND alumno_id = ? AND ritmo_tipo_origen = 'RELATIVO'
      AND COALESCE(ritmo_referencia_distancia, ritmo_falta_marca) = ?
    """.trimIndent()

/** `COALESCE` sobre el máximo: una proyección vacía da lag 0, no un lag infinito que dispararía la alarma. */
private val LAG_SQL =
    """
    SELECT EXTRACT(EPOCH FROM (now() - COALESCE(MAX(last_processed_event_ts), now())))::BIGINT
    FROM seguimiento.plan_resuelto_por_alumno
    """.trimIndent()
