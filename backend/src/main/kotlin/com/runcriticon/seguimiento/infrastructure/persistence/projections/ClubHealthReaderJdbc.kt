package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ClubHealthReader
import com.runcriticon.seguimiento.domain.GroupActivity
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/**
 * Adaptador de [ClubHealthReader] sobre `JdbcTemplate`.
 *
 * El `JOIN` por las cuatro columnas es el mismo que usa `CoachAlertReaderJdbc.REPORTS_SQL`:
 * `reporte_sesion` no guarda `grupo_id`, lo aporta la sesión resuelta.
 *
 * `p.grupo_id IS NOT NULL` excluye las filas proyectadas antes de que la proyección empezara a guardar el
 * grupo: no hubo forma de rellenarlas retroactivamente, y atribuir su reporte a algún grupo sería
 * inventarlo. Un grupo cuya única actividad sea de ese periodo se pinta como "sin actividad" hasta que su
 * entrenador vuelva a publicar un plan — es el comportamiento correcto, no un hueco.
 */
@Repository
class ClubHealthReaderJdbc(
    private val jdbc: JdbcTemplate,
) : ClubHealthReader {
    @AuthScope(Scope.CLUB)
    override fun findLastActivityByGroup(clubId: ClubId): List<GroupActivity> =
        jdbc.query(
            LAST_ACTIVITY_BY_GROUP_SQL,
            { rs: ResultSet, _: Int ->
                GroupActivity(
                    groupId = GroupId.of(rs.getObject("grupo_id", UUID::class.java)),
                    lastReportedAt = rs.getTimestamp("ultima_actividad").toInstant(),
                )
            },
            clubId.value,
        )
}

// A nivel de fichero, no en `companion object`: un val de companion genera un accesor sintético público
// que la malla anti-IDOR contaría como método del `@Repository` sin `@AuthScope`.
private const val LAST_ACTIVITY_BY_GROUP_SQL =
    """
    SELECT p.grupo_id, MAX(r.reportado_en) AS ultima_actividad
    FROM seguimiento.reporte_sesion r
    JOIN seguimiento.plan_resuelto_por_alumno p
        ON p.alumno_id = r.alumno_id AND p.plan_id = r.plan_id
       AND p.dia = r.dia AND p.club_id = r.club_id
    WHERE r.club_id = ?
      AND p.grupo_id IS NOT NULL
    GROUP BY p.grupo_id
    """
