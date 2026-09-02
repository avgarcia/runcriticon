package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanReader
import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.AdjustmentReason
import com.runcriticon.seguimiento.domain.DayAdjustment
import com.runcriticon.seguimiento.domain.NotDoneReason
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ReportStatus
import com.runcriticon.seguimiento.domain.ResolvedPace
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionReport
import com.runcriticon.seguimiento.domain.SessionType
import com.runcriticon.seguimiento.domain.SessionVolume
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.infrastructure.persistence.RESOLVED_SESSION_MAPPER
import com.runcriticon.seguimiento.infrastructure.persistence.ResolvedSessionPayload
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.LocalDate
import java.util.UUID

/**
 * Adaptador de [ResolvedPlanReader] sobre `JdbcTemplate`.
 *
 * Solo `@AuthScope(Scope.CLUB)`, no `Scope.OWNED`: `AuthScopeEnforcementAspect` todavía no implementa la
 * verificación de ningún scope salvo `CLUB` y **falla cerrado** ante cualquier otro declarado (ver su KDoc) —
 * añadir `Scope.OWNED` aquí convertiría toda llamada real en un 403 por `AuthScopeViolationException`, no en
 * una verificación adicional. No hace falta: `studentId` nunca llega de un `alumnoId` de entrada (el endpoint
 * es `/me/plan`, sin path variable) — siempre es `StudentId.of(actor.userId)` en `GetMyWeekQuery`, así que no
 * hay vector IDOR que este scope tuviera que cerrar.
 *
 * `LEFT JOIN` con `seguimiento.reporte_sesion` (LAL-30) sobre la clave natural completa
 * `(alumno_id, plan_id, dia)`, que comparten ambas tablas: no multiplica filas (como mucho un reporte por
 * fila resuelta) y trae el reporte, si existe, en la misma consulta que ya resuelve la semana — la tira
 * necesita el indicador ✓/⚡/✗ por día sin una segunda ida a la base de datos.
 *
 * **Overlay de reajuste (LAL-33)**: `LEFT JOIN` adicional con `seguimiento.reajuste_dia` sobre la misma clave
 * natural `(alumno_id, plan_id, dia)` — `dia` en `reajuste_dia` es siempre el día PLANIFICADO. El día
 * EFECTIVO que ve el alumno es `COALESCE(reajuste.dia_destino, p.dia)`: una sesión `MOVIDA` desaparece de su
 * día planificado y aparece en `dia_destino`; una `SALTADA` no se mueve, solo lleva la marca. `plan_resuelto_
 * por_alumno` **nunca se escribe** desde este flujo — la proyección la escriben en exclusiva los listeners de
 * `planificacion` (ADR-0002 D5, snapshot congelado); el reajuste vive solo en la ruta de lectura.
 */
@Repository
class ResolvedPlanReaderJdbc(
    private val jdbc: JdbcTemplate,
) : ResolvedPlanReader {
    @AuthScope(Scope.CLUB)
    override fun findWeek(
        clubId: ClubId,
        studentId: StudentId,
        from: LocalDate,
        to: LocalDate,
    ): List<ResolvedSession> =
        jdbc.query(
            FIND_WEEK_SQL,
            { rs: ResultSet, _: Int -> toResolvedSession(rs) },
            clubId.value,
            clubId.value,
            clubId.value,
            studentId.value,
            from,
            to,
        )

    @AuthScope(Scope.CLUB)
    override fun findDay(
        clubId: ClubId,
        studentId: StudentId,
        day: LocalDate,
    ): ResolvedSession? =
        jdbc
            .query(
                FIND_DAY_SQL,
                { rs: ResultSet, _: Int -> toResolvedSession(rs) },
                clubId.value,
                clubId.value,
                clubId.value,
                studentId.value,
                day,
            ).firstOrNull()
}

private fun toResolvedSession(rs: ResultSet): ResolvedSession {
    val payload = RESOLVED_SESSION_MAPPER.readValue(rs.getString("sesion_resuelta"), ResolvedSessionPayload::class.java)
    return ResolvedSession(
        day = rs.getObject("dia", LocalDate::class.java),
        plannedDay = rs.getObject("dia_planificada", LocalDate::class.java),
        planId = PlanId.of(rs.getObject("plan_id", UUID::class.java)),
        type = SessionType.valueOf(payload.tipo),
        volume = toVolume(payload),
        pace = toPace(rs),
        notes = payload.notas,
        messageToStudent = rs.getString("mensaje_al_alumno"),
        isPersonalized = rs.getBoolean("es_personalizada"),
        report = toReport(rs),
        adjustment = toAdjustment(rs),
    )
}

private fun toVolume(payload: ResolvedSessionPayload): SessionVolume? =
    when (payload.volumenTipo) {
        "DISTANCIA" -> payload.volumenMetros?.let { SessionVolume.Distance(it) }
        "TIEMPO" -> payload.volumenMinutos?.let { SessionVolume.Duration(it) }
        else -> null
    }

private fun toPace(rs: ResultSet): ResolvedPace? {
    val origin = rs.getString("ritmo_tipo_origen") ?: return null
    val secondsPerKm = rs.getObject("ritmo_calculado_seg_por_km", Int::class.javaObjectType)
    return when (origin) {
        "ABSOLUTO" -> secondsPerKm?.let { ResolvedPace.Absolute(it) }
        "RELATIVO" ->
            (rs.getString("ritmo_referencia_distancia") ?: rs.getString("ritmo_falta_marca"))
                ?.toRaceDistance()
                ?.let { reference ->
                    ResolvedPace.Relative(
                        reference = reference,
                        deltaSecondsPerKm = rs.getObject("ritmo_delta_seg_por_km", Int::class.javaObjectType),
                        secondsPerKm = secondsPerKm,
                    )
                }
        else -> null
    }
}

/** `reporte_estado` viene de un `LEFT JOIN`: `null` significa que el alumno todavía no ha reportado ese día. */
private fun toReport(rs: ResultSet): SessionReport? {
    val status = rs.getString("reporte_estado")?.let { ReportStatus.valueOf(it) } ?: return null
    return SessionReport(
        status = status,
        rating = rs.getObject("reporte_valoracion", Int::class.javaObjectType),
        reason = rs.getString("reporte_motivo")?.let { NotDoneReason.valueOf(it) },
        notes = rs.getString("reporte_notas"),
        painFlag = rs.getBoolean("reporte_marca_dolor"),
        reportedAt = rs.getTimestamp("reporte_reportado_en").toInstant(),
    )
}

/** `reajuste_accion` viene de un `LEFT JOIN`: `null` significa que la sesión no tiene reajuste (LAL-33). */
private fun toAdjustment(rs: ResultSet): DayAdjustment? {
    val action = rs.getString("reajuste_accion")?.let { AdjustmentAction.valueOf(it) } ?: return null
    return DayAdjustment(
        operationId = rs.getObject("reajuste_operacion_id", UUID::class.java),
        action = action,
        plannedDay = rs.getObject("dia_planificada", LocalDate::class.java),
        targetDay = rs.getObject("reajuste_dia_destino", LocalDate::class.java),
        reason = AdjustmentReason.valueOf(rs.getString("reajuste_motivo")),
        message = rs.getString("reajuste_mensaje"),
        painFlag = rs.getBoolean("reajuste_marca_dolor"),
        createdAt = rs.getTimestamp("reajuste_creado_en").toInstant(),
    )
}

private fun String.toRaceDistance(): RaceDistance? =
    when (this) {
        "5K" -> RaceDistance.FIVE_K
        "10K" -> RaceDistance.TEN_K
        "21K" -> RaceDistance.HALF_MARATHON
        "42K" -> RaceDistance.MARATHON
        else -> null
    }

// A nivel de fichero, no en `companion object` (ver justificación en el resto de repos JDBC del monorepo).
//
// `r.club_id = ?` / `a.club_id = ?` como parámetro ligado, no `r.club_id = p.club_id`: mismo criterio
// anti-IDOR que el resto de repos JDBC del monorepo — nunca confiar en una igualdad fila-a-fila entre tablas
// para el filtro de tenancy.
private const val JOIN_REPORT =
    """
    LEFT JOIN seguimiento.reporte_sesion r
        ON r.alumno_id = p.alumno_id AND r.plan_id = p.plan_id AND r.dia = p.dia AND r.club_id = ?
    """

private const val JOIN_ADJUSTMENT =
    """
    LEFT JOIN seguimiento.reajuste_dia a
        ON a.alumno_id = p.alumno_id AND a.plan_id = p.plan_id AND a.dia = p.dia AND a.club_id = ?
    """

private const val REPORT_COLUMNS =
    """
    r.estado AS reporte_estado, r.valoracion AS reporte_valoracion, r.motivo AS reporte_motivo,
    r.notas AS reporte_notas, r.marca_dolor AS reporte_marca_dolor, r.reportado_en AS reporte_reportado_en
    """

private const val ADJUSTMENT_COLUMNS =
    """
    a.operacion_id AS reajuste_operacion_id, a.accion AS reajuste_accion, a.dia_destino AS reajuste_dia_destino,
    a.motivo AS reajuste_motivo, a.mensaje AS reajuste_mensaje, a.marca_dolor AS reajuste_marca_dolor,
    a.creado_en AS reajuste_creado_en
    """

/** El día EFECTIVO que ve el alumno: `dia_destino` si la sesión está `MOVIDA`, si no el día planificado. */
private const val EFFECTIVE_DAY = "COALESCE(a.dia_destino, p.dia)"

// `DISTINCT ON (día efectivo)` desempata dos colisiones posibles: (a) el caso ya existente "el alumno
// pertenece a dos grupos que publican plan la misma semana" (los grupos son consultas sobre tags, no
// excluyentes), y (b) desde LAL-33, dos sesiones cuyo día efectivo coincide tras un reajuste — cerrado en su
// mayoría por el índice único `reajuste_dia_destino_unico_idx`, pero dos sesiones NO movidas del mismo día
// planificado (caso a) siguen necesitando este desempate. Se queda la fila del evento procesado más reciente
// y, en empate exacto de timestamp, la de mayor `plan_id` — mismo criterio que antes de LAL-33.
private val FIND_WEEK_SQL =
    """
    SELECT DISTINCT ON ($EFFECTIVE_DAY)
        p.dia AS dia_planificada, $EFFECTIVE_DAY AS dia,
        p.plan_id, p.sesion_resuelta::text AS sesion_resuelta, p.ritmo_tipo_origen,
        p.ritmo_calculado_seg_por_km, p.ritmo_referencia_distancia, p.ritmo_falta_marca,
        p.ritmo_delta_seg_por_km, p.mensaje_al_alumno, p.es_personalizada,
        $REPORT_COLUMNS,
        $ADJUSTMENT_COLUMNS
    FROM seguimiento.plan_resuelto_por_alumno p
    $JOIN_REPORT
    $JOIN_ADJUSTMENT
    WHERE p.club_id = ? AND p.alumno_id = ? AND $EFFECTIVE_DAY BETWEEN ? AND ?
    ORDER BY $EFFECTIVE_DAY, p.last_processed_event_ts DESC, p.plan_id DESC
    """.trimIndent()

/** Mismo desempate que [FIND_WEEK_SQL], acotado a un único día EFECTIVO. */
private val FIND_DAY_SQL =
    """
    SELECT
        p.dia AS dia_planificada, $EFFECTIVE_DAY AS dia,
        p.plan_id, p.sesion_resuelta::text AS sesion_resuelta, p.ritmo_tipo_origen,
        p.ritmo_calculado_seg_por_km, p.ritmo_referencia_distancia, p.ritmo_falta_marca,
        p.ritmo_delta_seg_por_km, p.mensaje_al_alumno, p.es_personalizada,
        $REPORT_COLUMNS,
        $ADJUSTMENT_COLUMNS
    FROM seguimiento.plan_resuelto_por_alumno p
    $JOIN_REPORT
    $JOIN_ADJUSTMENT
    WHERE p.club_id = ? AND p.alumno_id = ? AND $EFFECTIVE_DAY = ?
    ORDER BY p.last_processed_event_ts DESC, p.plan_id DESC
    LIMIT 1
    """.trimIndent()
