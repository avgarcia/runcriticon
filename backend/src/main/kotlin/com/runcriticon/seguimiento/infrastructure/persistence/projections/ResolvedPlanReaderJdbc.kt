package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanReader
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ResolvedPace
import com.runcriticon.seguimiento.domain.ResolvedSession
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

/**
 * Adaptador de [ResolvedPlanReader] sobre `JdbcTemplate`.
 *
 * Solo `@AuthScope(Scope.CLUB)`, no `Scope.OWNED`: `AuthScopeEnforcementAspect` todavía no implementa la
 * verificación de ningún scope salvo `CLUB` y **falla cerrado** ante cualquier otro declarado (ver su KDoc) —
 * añadir `Scope.OWNED` aquí convertiría toda llamada real en un 403 por `AuthScopeViolationException`, no en
 * una verificación adicional. No hace falta: `studentId` nunca llega de un `alumnoId` de entrada (el endpoint
 * es `/me/plan`, sin path variable) — siempre es `StudentId.of(actor.userId)` en `GetMyWeekQuery`, así que no
 * hay vector IDOR que este scope tuviera que cerrar.
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
            studentId.value,
            from,
            to,
        )
}

private fun toResolvedSession(rs: ResultSet): ResolvedSession {
    val payload = RESOLVED_SESSION_MAPPER.readValue(rs.getString("sesion_resuelta"), ResolvedSessionPayload::class.java)
    return ResolvedSession(
        day = rs.getObject("dia", LocalDate::class.java),
        type = SessionType.valueOf(payload.tipo),
        volume = toVolume(payload),
        pace = toPace(rs),
        notes = payload.notas,
        messageToStudent = rs.getString("mensaje_al_alumno"),
        isPersonalized = rs.getBoolean("es_personalizada"),
    )
}

private fun toVolume(payload: ResolvedSessionPayload): SessionVolume? =
    when (payload.volumenTipo) {
        "DISTANCIA" -> payload.volumenMetros?.let { SessionVolume.Distance(it) }
        "TIEMPO" -> payload.volumenMinutos?.let { SessionVolume.Duration(it) }
        else -> null
    }

private fun toPace(rs: ResultSet): ResolvedPace? {
    val secondsPerKm = rs.getObject("ritmo_calculado_seg_por_km", Int::class.javaObjectType)
    val referenceDistance = rs.getString("ritmo_referencia_distancia")?.toRaceDistance()
    val missingMark = rs.getString("ritmo_falta_marca")?.toRaceDistance()
    if (secondsPerKm == null && referenceDistance == null && missingMark == null) return null
    return ResolvedPace(secondsPerKm = secondsPerKm, referenceDistance = referenceDistance, missingMark = missingMark)
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
// `DISTINCT ON (dia)` desempata el caso "el alumno pertenece a dos grupos que publican plan la misma semana"
// (los grupos son consultas sobre tags, no excluyentes): se queda la fila del evento procesado más reciente
// y, en empate exacto de timestamp, la de mayor `plan_id`. Desempate determinista pero arbitrario a efectos de
// negocio — avisar al alumno de la colisión real queda diferido (ver el README del módulo).
private val FIND_WEEK_SQL =
    """
    SELECT DISTINCT ON (dia)
        dia, sesion_resuelta::text AS sesion_resuelta, ritmo_calculado_seg_por_km,
        ritmo_referencia_distancia, ritmo_falta_marca, mensaje_al_alumno, es_personalizada
    FROM seguimiento.plan_resuelto_por_alumno
    WHERE club_id = ? AND alumno_id = ? AND dia BETWEEN ? AND ?
    ORDER BY dia, last_processed_event_ts DESC, plan_id DESC
    """.trimIndent()
