package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.CoachAlertReader
import com.runcriticon.seguimiento.domain.CoachAlert
import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.matchesPaceOffTargetHeuristic
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Ventana de "activo" para dolor y ritmo fuera de objetivo (LAL-116): más allá de esto es ruido, no
 * excepción — ver el KDoc de [CoachAlertReader]. */
private const val ALERT_WINDOW_DAYS = 7L

/** Zona del club piloto (mono-club, ADR-0006 D1), mismo criterio que `GetMyWeekQuery.CLUB_ZONE`: convertir un
 * `Instant` a día natural para comparar contra `today` sin desfases de medianoche. */
private val CLUB_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

/**
 * Adaptador de [CoachAlertReader] sobre `JdbcTemplate`. Dos consultas independientes, sin unión SQL: dolor y
 * ritmo fuera de objetivo comparten origen (`reporte_sesion` reciente) pero "sin reportar" necesita una
 * forma completamente distinta de consulta (agregación por alumno de la fecha del último reporte, incluidos
 * los alumnos que **no** tienen ninguna fila en `reporte_sesion`) — forzarlas a una sola sentencia habría
 * significado un `FULL OUTER JOIN` mucho menos legible para ahorrar un round-trip irrelevante a este volumen.
 *
 * Ambas acotan "solo mis grupos" con la misma subconsulta `coach_groups` contra `grupo_entrenador`
 * (alimentada por [com.runcriticon.seguimiento.application.listeners.CoachGroupProjectionListener]) — un
 * `grupoId` ajeno al entrenador, o inexistente, simplemente no aparece en ningún resultado (mismo criterio
 * que `GET /planes`, sin 403 ni 404 diferenciados).
 */
@Repository
class CoachAlertReaderJdbc(
    private val jdbc: JdbcTemplate,
) : CoachAlertReader {
    @AuthScope(Scope.CLUB)
    override fun findActiveAlerts(
        clubId: ClubId,
        coachId: CoachId,
        groupId: GroupId?,
        today: LocalDate,
    ): List<CoachAlert> {
        val since = today.minusDays(ALERT_WINDOW_DAYS)
        val reportAlerts = findReportAlerts(clubId, coachId, groupId, since)
        val noReportAlerts = findNoReportAlerts(clubId, coachId, groupId, today, since)
        return reportAlerts + noReportAlerts
    }

    /** Fila candidata de `reporte_sesion`: de aquí salen tanto [CoachAlert.PainReported] como
     * [CoachAlert.PaceOffTarget], según qué columna dispare la alerta. */
    private fun findReportAlerts(
        clubId: ClubId,
        coachId: CoachId,
        groupId: GroupId?,
        since: LocalDate,
    ): List<CoachAlert> {
        val sql = REPORTS_SQL + (groupId?.let { " AND p.grupo_id = ?" } ?: "")
        val args = mutableListOf<Any>(clubId.value, coachId.value, clubId.value, since)
        groupId?.let { args += it.value }

        val rows =
            jdbc.query(sql, { rs: ResultSet, _: Int -> rs.toReportRow() }, *args.toTypedArray())

        val pain =
            rows.filter { it.painFlag }.map { row ->
                CoachAlert.PainReported(
                    studentId = row.studentId,
                    groupId = row.groupId,
                    day = row.day,
                    notes = row.notes,
                    reportedAt = row.reportedAtInstant,
                )
            }
        val paceOffTarget =
            rows.mapNotNull { row ->
                row.notes
                    ?.takeIf(::matchesPaceOffTargetHeuristic)
                    ?.let { notes ->
                        CoachAlert.PaceOffTarget(
                            studentId = row.studentId,
                            groupId = row.groupId,
                            day = row.day,
                            notes = notes,
                        )
                    }
            }
        return pain + paceOffTarget
    }

    /** Alumnos de los grupos del entrenador sin ningún reporte en los últimos [ALERT_WINDOW_DAYS] días —
     * incluidos los que nunca han reportado nada, ancla entonces en su primera sesión resuelta. */
    private fun findNoReportAlerts(
        clubId: ClubId,
        coachId: CoachId,
        groupId: GroupId?,
        today: LocalDate,
        since: LocalDate,
    ): List<CoachAlert> {
        val sql = NO_REPORT_SQL_TEMPLATE.format(if (groupId != null) "AND grupo_id = ?" else "")
        val args = mutableListOf<Any>(clubId.value, coachId.value, clubId.value)
        groupId?.let { args += it.value }
        args += clubId.value
        args += since

        return jdbc.query(sql, { rs: ResultSet, _: Int -> rs.toNoReportAlert(today) }, *args.toTypedArray())
    }
}

private data class ReportRow(
    val studentId: StudentId,
    val groupId: GroupId,
    val day: LocalDate,
    val notes: String?,
    val painFlag: Boolean,
    val reportedAtInstant: Instant,
)

private fun ResultSet.toReportRow(): ReportRow =
    ReportRow(
        studentId = StudentId.of(getObject("alumno_id", UUID::class.java)),
        groupId = GroupId.of(getObject("grupo_id", UUID::class.java)),
        day = getObject("dia", LocalDate::class.java),
        notes = getString("notas"),
        painFlag = getBoolean("marca_dolor"),
        reportedAtInstant = getTimestamp("reportado_en").toInstant(),
    )

private fun ResultSet.toNoReportAlert(today: LocalDate): CoachAlert.NoReportInDays {
    val lastReportedAt = getTimestamp("ultimo_reporte")?.toInstant()
    val lastReportDay =
        lastReportedAt?.atZone(CLUB_ZONE)?.toLocalDate()
            ?: getObject("primera_sesion", LocalDate::class.java)
    return CoachAlert.NoReportInDays(
        studentId = StudentId.of(getObject("alumno_id", UUID::class.java)),
        groupId = GroupId.of(getObject("grupo_id", UUID::class.java)),
        daysSinceLastReport = ChronoUnit.DAYS.between(lastReportDay, today),
        lastReportedAt = lastReportedAt,
    )
}

// A nivel de fichero, no en `companion object`: un val de companion genera un accesor sintético público que
// AuthorizationArchTest contaría como método sin `@AuthScope`/`@NoAuthScope`.
private const val COACH_GROUPS_CTE =
    """
    WITH coach_groups AS (
        SELECT grupo_id FROM seguimiento.grupo_entrenador WHERE club_id = ? AND entrenador_id = ?
    )
    """

/** `p.club_id = ? AND r.club_id = p.club_id` vía el propio JOIN, no una igualdad fila-a-fila: mismo criterio
 * anti-IDOR que el resto de repos JDBC del monorepo. El filtro de club real es el de [COACH_GROUPS_CTE]. */
private val REPORTS_SQL =
    """
    $COACH_GROUPS_CTE
    SELECT p.alumno_id, p.grupo_id, p.dia, r.notas, r.marca_dolor, r.reportado_en
    FROM seguimiento.reporte_sesion r
    JOIN seguimiento.plan_resuelto_por_alumno p
        ON p.alumno_id = r.alumno_id AND p.plan_id = r.plan_id AND p.dia = r.dia AND p.club_id = r.club_id
    WHERE r.club_id = ?
      AND p.grupo_id IN (SELECT grupo_id FROM coach_groups)
      AND p.dia >= ?
      AND (r.marca_dolor = TRUE OR r.notas IS NOT NULL)
    """.trimIndent()

/**
 * `%s` se sustituye en Kotlin por el filtro opcional `AND grupo_id = ?` (o cadena vacía) — `String.format`
 * en vez de interpolación directa para dejar claro en el propio literal dónde va el hueco.
 *
 * `student_groups` agrupa por `(alumno_id, grupo_id)` con `MIN(dia)` como ancla de "primera sesión" para el
 * alumno que nunca ha reportado nada — sin fila en `reporte_sesion`, `left_report` no lo trae y `COALESCE`
 * cae a `primera_sesion`.
 */
private const val NO_REPORT_SQL_TEMPLATE =
    """
    WITH coach_groups AS (
        SELECT grupo_id FROM seguimiento.grupo_entrenador WHERE club_id = ? AND entrenador_id = ?
    ),
    student_groups AS (
        SELECT alumno_id, grupo_id, MIN(dia) AS primera_sesion
        FROM seguimiento.plan_resuelto_por_alumno
        WHERE club_id = ? AND grupo_id IN (SELECT grupo_id FROM coach_groups) %s
        GROUP BY alumno_id, grupo_id
    ),
    last_report AS (
        SELECT alumno_id, MAX(reportado_en) AS ultimo_reporte
        FROM seguimiento.reporte_sesion
        WHERE club_id = ?
        GROUP BY alumno_id
    )
    SELECT sg.alumno_id, sg.grupo_id, sg.primera_sesion, lr.ultimo_reporte
    FROM student_groups sg
    LEFT JOIN last_report lr ON lr.alumno_id = sg.alumno_id
    WHERE COALESCE(lr.ultimo_reporte::date, sg.primera_sesion) <= ?
    """
