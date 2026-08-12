package com.runcriticon.clubtaxonomia.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.CoachDirectory
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.AssignedGroup
import com.runcriticon.clubtaxonomia.domain.person.CoachWorkload
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/**
 * Adaptador de [CoachDirectory] sobre `JdbcTemplate`, en la línea de [StudentDirectoryJdbc]: SQL plano, sin
 * `@Entity`, sobre `club_taxonomia.persona`.
 *
 * **Dos consultas, no una.** La primera trae a todos los entrenadores del club, exista o no asignación (LAL-93
 * puede llevar semanas sin que nadie asigne a nadie, y ese entrenador sigue teniendo que aparecer). La segunda solo
 * resuelve la membresía de los grupos que **de verdad** lleva alguien: unirla a la primera con un `LEFT JOIN` desde
 * cero entrenadores sería resolver la membresía de grupos sin entrenador para tirarla, y a la escala prevista
 * —un puñado de grupos por entrenador— la resolución en dos pasos es más barata y más legible que forzarlo en una
 * sola sentencia.
 */
@Repository
class CoachDirectoryJdbc(
    private val jdbc: JdbcTemplate,
) : CoachDirectory {
    @AuthScope(Scope.CLUB)
    override fun listByClub(clubId: ClubId): List<CoachWorkload> {
        val coaches = jdbc.query(LIST_ALL_SQL, { rs: ResultSet, _: Int -> toBaseCoach(rs) }, clubId.value)
        if (coaches.isEmpty()) return emptyList()

        val rows =
            jdbc.query(
                LOAD_WORKLOAD_SQL,
                { rs: ResultSet, _: Int -> toWorkloadRow(rs) },
                *Array(WORKLOAD_CLUB_PARAMS) { clubId.value },
            )
        val rowsByCoach = rows.groupBy { it.coachId }

        return coaches.map { coach -> toWorkload(coach, rowsByCoach[coach.id.value].orEmpty()) }
    }
}

// SQL a nivel de fichero, no en `companion object`: una propiedad privada del companion leída desde la clase genera
// un accesor sintético público que la malla anti-IDOR contaría como método del `@Repository` sin `@AuthScope`.

private data class BaseCoach(
    val id: PersonId,
    val name: String,
    val email: String,
    val status: PersonStatus,
)

/** Fila cruda de [LOAD_WORKLOAD_SQL]: un entrenador, un grupo que lleva, y **como mucho** un alumno de ese grupo. */
private data class WorkloadRow(
    val coachId: UUID,
    val groupId: UUID,
    val groupName: String,
    val studentId: UUID?,
)

private fun toBaseCoach(rs: ResultSet): BaseCoach =
    BaseCoach(
        id = PersonId.of(rs.getObject("id", UUID::class.java)),
        name = rs.getString("nombre"),
        email = rs.getString("email"),
        status = PersonStatus.valueOf(rs.getString("estado")),
    )

private fun toWorkloadRow(rs: ResultSet): WorkloadRow =
    WorkloadRow(
        coachId = rs.getObject("entrenador_id", UUID::class.java),
        groupId = rs.getObject("grupo_id", UUID::class.java),
        groupName = rs.getString("grupo_nombre"),
        studentId = rs.getObject("alumno_id", UUID::class.java),
    )

/**
 * Compone la carga de un entrenador a partir de sus filas. `groups` sale ordenada por nombre porque
 * [LOAD_WORKLOAD_SQL] ya la ordena así y `groupBy` conserva el orden de aparición.
 *
 * `totalStudents` es `COUNT(DISTINCT alumno)`, **no la suma** de `totalStudents` de cada grupo: un alumno en dos
 * grupos de este entrenador es una persona, no dos, y la carga mide personas (documentado también en el contrato,
 * `CoachWorkloadResponse.totalAlumnos`).
 */
private fun toWorkload(
    coach: BaseCoach,
    rows: List<WorkloadRow>,
): CoachWorkload {
    val groups =
        rows
            .groupBy { it.groupId to it.groupName }
            .map { (group, groupRows) ->
                AssignedGroup(
                    id = GroupId.of(group.first),
                    name = group.second,
                    totalStudents = groupRows.mapNotNull { it.studentId }.distinct().size,
                )
            }
    return CoachWorkload(
        id = coach.id,
        name = coach.name,
        email = coach.email,
        status = coach.status,
        groups = groups,
        totalStudents = rows.mapNotNull { it.studentId }.distinct().size,
    )
}

private const val LIST_ALL_SQL =
    """
    SELECT p.id, p.nombre, p.email, p.estado
    FROM club_taxonomia.persona p
    WHERE p.club_id = ? AND p.rol = 'ENTRENADOR'
    ORDER BY p.nombre, p.id
    """

/** Los ocho `?` de [LOAD_WORKLOAD_SQL] reciben todos el mismo club, así que no hace falta orden posicional. */
private const val WORKLOAD_CLUB_PARAMS = 8

/**
 * Membresía resuelta de **solo** los grupos que tienen al menos un entrenador asignado, con el mismo criterio
 * `(cumple ∨ incluido) ∧ ¬excluido` que ya usa `GroupRepositoryJdbc` (ADR-0002 D3+D4) -- se duplica en vez de
 * compartirse con esa consulta, mismo motivo que documentan las CTEs allí: cada predicado tiene que poder auditarse
 * de un vistazo con su `club_id = ?` propio, sin indirección de subquery contra `grupo`.
 *
 * Un grupo sin ningún miembro válido sigue apareciendo (una fila por `(entrenador, grupo)` con `alumno_id NULL`,
 * gracias al `LEFT JOIN`): un entrenador con un grupo vacío tiene que poder verse con `totalAlumnos = 0`, no
 * desaparecer de la lista.
 */
private const val LOAD_WORKLOAD_SQL =
    """
    WITH asignaciones AS (
        SELECT entrenador_id, grupo_id FROM club_taxonomia.grupo_entrenador WHERE club_id = ?
    ),
    grupos AS (
        SELECT DISTINCT g.id, g.nombre
        FROM club_taxonomia.grupo g
        JOIN asignaciones a ON a.grupo_id = g.id
        WHERE g.club_id = ?
    ),
    filtros AS (
        SELECT grupo_id, COUNT(*) AS exigidos
        FROM club_taxonomia.grupo_tag_requerido
        WHERE club_id = ?
        GROUP BY grupo_id
    ),
    cumplen_tags AS (
        SELECT gtr.grupo_id, at.alumno_id
        FROM club_taxonomia.grupo_tag_requerido gtr
        JOIN filtros f ON f.grupo_id = gtr.grupo_id
        JOIN club_taxonomia.alumno_tag at ON at.tag_value_id = gtr.tag_value_id
        WHERE gtr.club_id = ? AND at.club_id = ?
        GROUP BY gtr.grupo_id, at.alumno_id, f.exigidos
        HAVING COUNT(DISTINCT gtr.tag_value_id) = f.exigidos
    ),
    incluidos AS (
        SELECT grupo_id, alumno_id FROM club_taxonomia.grupo_alumno_override
        WHERE club_id = ? AND incluido = TRUE
    ),
    excluidos AS (
        SELECT grupo_id, alumno_id FROM club_taxonomia.grupo_alumno_override
        WHERE club_id = ? AND incluido = FALSE
    ),
    miembros AS (
        SELECT grupo_id, alumno_id FROM cumplen_tags
        UNION
        SELECT grupo_id, alumno_id FROM incluidos
        EXCEPT
        SELECT grupo_id, alumno_id FROM excluidos
    ),
    miembros_validos AS (
        SELECT m.grupo_id, m.alumno_id
        FROM miembros m
        JOIN club_taxonomia.persona p ON p.id = m.alumno_id
        WHERE p.club_id = ? AND p.rol = 'ALUMNO'
    )
    SELECT a.entrenador_id,
           g.id     AS grupo_id,
           g.nombre AS grupo_nombre,
           mv.alumno_id
    FROM asignaciones a
    JOIN grupos g ON g.id = a.grupo_id
    LEFT JOIN miembros_validos mv ON mv.grupo_id = a.grupo_id
    ORDER BY a.entrenador_id, g.nombre, g.id
    """
