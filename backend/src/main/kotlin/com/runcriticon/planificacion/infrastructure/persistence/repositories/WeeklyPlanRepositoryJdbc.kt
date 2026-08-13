package com.runcriticon.planificacion.infrastructure.persistence.repositories

import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.Pace
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.Personalization
import com.runcriticon.planificacion.domain.PersonalizationId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanStatus
import com.runcriticon.planificacion.domain.RaceDistance
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/**
 * Adaptador de [WeeklyPlanRepository] sobre `JdbcTemplate`. Sin `@Entity`/Hibernate, mismo motivo que
 * `GroupRepositoryJdbc` de `club_taxonomia`: el repo entero usa JDBC plano, no un ORM.
 *
 * "Carga eager" (ADR-0008 D17) aquí significa **siempre las tres tablas, sin proxies perezosos** — no
 * literalmente una única sentencia SQL, que con JDBC plano exigiría un JOIN triple y desnormalizar en Kotlin sin
 * beneficio real a este volumen (un plan tiene ~7 sesiones y unas pocas personalizaciones). Tres consultas
 * acotadas por `plan_id`, siempre las tres, nunca bajo demanda.
 */
@Repository
class WeeklyPlanRepositoryJdbc(
    private val jdbc: JdbcTemplate,
) : WeeklyPlanRepository {
    @AuthScope(Scope.CLUB)
    override fun save(
        clubId: ClubId,
        plan: WeeklyPlan,
    ) {
        jdbc.update(
            INSERT_PLAN_SQL,
            plan.id.value,
            clubId.value,
            plan.groupId.value,
            plan.coachId.value,
            plan.week,
            plan.status.name,
        )
        // Sin `batchUpdate`: los argumentos de `sesion` llevan `null` cuando la sesión no tiene ritmo todavía, y el
        // array resultante (`Array<Any?>`) no encaja en la sobrecarga `List<Array<out Any>>` de `batchUpdate`. A este
        // volumen (~7 sesiones por plan) el coste de una sentencia por fila es irrelevante.
        plan.sessions.forEach { session -> jdbc.update(INSERT_SESSION_SQL, *sessionInsertArgs(plan.id, session)) }
        plan.personalizations.forEach { personalization ->
            jdbc.update(INSERT_PERSONALIZATION_SQL, *personalizationInsertArgs(plan.id, personalization))
        }
    }

    @AuthScope(Scope.CLUB)
    override fun findById(
        clubId: ClubId,
        id: PlanId,
    ): WeeklyPlan? {
        val plan =
            jdbc
                .query(FIND_PLAN_SQL, { rs: ResultSet, _: Int -> toPlan(rs, clubId) }, id.value, clubId.value)
                .firstOrNull() ?: return null
        val sessions = jdbc.query(FIND_SESSIONS_SQL, { rs: ResultSet, _: Int -> toSession(rs) }, id.value)
        val personalizations =
            jdbc.query(FIND_PERSONALIZATIONS_SQL, { rs: ResultSet, _: Int -> toPersonalization(rs) }, id.value)
        return plan.copy(sessions = sessions, personalizations = personalizations)
    }

    @AuthScope(Scope.CLUB)
    override fun listDraftsByGroup(
        clubId: ClubId,
        groupId: GroupId,
    ): List<WeeklyPlan> =
        jdbc.query(
            LIST_DRAFTS_BY_GROUP_SQL,
            { rs: ResultSet, _: Int -> toPlan(rs, clubId) },
            clubId.value,
            groupId.value,
            PlanStatus.BORRADOR.name,
        )
    // Sin sesiones/personalizaciones: la pantalla de listado (AC7) solo necesita id/semana/estado, y cargarlas
    // aquí sería N+1 sobre una lista que puede tener varios planes. `findById` es la vía para el detalle completo.
}

private fun toPlan(
    rs: ResultSet,
    clubId: ClubId,
): WeeklyPlan =
    WeeklyPlan(
        id = PlanId.of(rs.getObject("id", UUID::class.java)),
        clubId = clubId,
        groupId = GroupId.of(rs.getObject("grupo_id", UUID::class.java)),
        coachId = PersonId.of(rs.getObject("entrenador_id", UUID::class.java)),
        week = rs.getDate("semana").toLocalDate(),
        status = PlanStatus.valueOf(rs.getString("estado")),
    )

private fun toSession(rs: ResultSet): Session =
    Session(
        id = SessionId.of(rs.getObject("id", UUID::class.java)),
        day = rs.getDate("dia").toLocalDate(),
        pace = toPace(rs),
    )

/** `ritmo_tipo` decide qué otras columnas leer (ADR-0002 D6); `null` si la sesión todavía no tiene ritmo. */
private fun toPace(rs: ResultSet): Pace? =
    when (rs.getString("ritmo_tipo")) {
        "ABSOLUTO" -> Pace.Absoluto(secondsPerKm = rs.getInt("ritmo_seg_por_km"))
        "RELATIVO" ->
            Pace.Relativo(
                reference = toRaceDistance(rs.getString("ritmo_ref_distancia")),
                deltaSecondsPerKm = rs.getInt("ritmo_delta_seg_por_km"),
            )
        else -> null
    }

private fun toRaceDistance(value: String): RaceDistance =
    when (value) {
        "5K" -> RaceDistance.FIVE_K
        "10K" -> RaceDistance.TEN_K
        "21K" -> RaceDistance.HALF_MARATHON
        "42K" -> RaceDistance.MARATHON
        else -> error("Distancia de referencia desconocida en planificacion.sesion: $value")
    }

private fun fromRaceDistance(distance: RaceDistance): String =
    when (distance) {
        RaceDistance.FIVE_K -> "5K"
        RaceDistance.TEN_K -> "10K"
        RaceDistance.HALF_MARATHON -> "21K"
        RaceDistance.MARATHON -> "42K"
    }

private fun toPersonalization(rs: ResultSet): Personalization =
    Personalization(
        id = PersonalizationId.of(rs.getObject("id", UUID::class.java)),
        sessionId = SessionId.of(rs.getObject("sesion_id", UUID::class.java)),
        studentId = PersonId.of(rs.getObject("alumno_id", UUID::class.java)),
        override = rs.getString("override"),
        messageToStudent = rs.getString("mensaje_al_alumno"),
    )

private fun sessionInsertArgs(
    planId: PlanId,
    session: Session,
): Array<Any?> {
    val pace = session.pace
    return arrayOf(
        session.id.value,
        planId.value,
        session.day,
        (pace as? Pace.Absoluto)?.let { "ABSOLUTO" } ?: (pace as? Pace.Relativo)?.let { "RELATIVO" },
        (pace as? Pace.Absoluto)?.secondsPerKm,
        (pace as? Pace.Relativo)?.let { fromRaceDistance(it.reference) },
        (pace as? Pace.Relativo)?.deltaSecondsPerKm,
    )
}

private fun personalizationInsertArgs(
    planId: PlanId,
    personalization: Personalization,
): Array<Any?> =
    arrayOf(
        personalization.id.value,
        planId.value,
        personalization.sessionId.value,
        personalization.studentId.value,
        personalization.override,
        personalization.messageToStudent,
    )

private const val INSERT_PLAN_SQL =
    """
    INSERT INTO planificacion.plan_semanal (id, club_id, grupo_id, entrenador_id, semana, estado)
    VALUES (?, ?, ?, ?, ?, ?)
    """

private const val INSERT_SESSION_SQL =
    """
    INSERT INTO planificacion.sesion
        (id, plan_id, dia, ritmo_tipo, ritmo_seg_por_km, ritmo_ref_distancia, ritmo_delta_seg_por_km)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """

private const val INSERT_PERSONALIZATION_SQL =
    """
    INSERT INTO planificacion.personalizacion (id, plan_id, sesion_id, alumno_id, override, mensaje_al_alumno)
    VALUES (?, ?, ?, ?, ?::jsonb, ?)
    """

private const val FIND_PLAN_SQL =
    """
    SELECT id, grupo_id, entrenador_id, semana, estado
    FROM planificacion.plan_semanal
    WHERE id = ? AND club_id = ?
    """

private const val FIND_SESSIONS_SQL =
    """
    SELECT id, dia, ritmo_tipo, ritmo_seg_por_km, ritmo_ref_distancia, ritmo_delta_seg_por_km
    FROM planificacion.sesion
    WHERE plan_id = ?
    ORDER BY dia, id
    """

private const val FIND_PERSONALIZATIONS_SQL =
    """
    SELECT id, sesion_id, alumno_id, override::text AS override, mensaje_al_alumno
    FROM planificacion.personalizacion
    WHERE plan_id = ?
    ORDER BY id
    """

private const val LIST_DRAFTS_BY_GROUP_SQL =
    """
    SELECT id, grupo_id, entrenador_id, semana, estado
    FROM planificacion.plan_semanal
    WHERE club_id = ? AND grupo_id = ? AND estado = ?
    ORDER BY semana, id
    """
