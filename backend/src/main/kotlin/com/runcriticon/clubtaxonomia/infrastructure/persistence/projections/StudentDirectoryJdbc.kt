package com.runcriticon.clubtaxonomia.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentDirectory
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

/**
 * Adaptador de [StudentDirectory] sobre `JdbcTemplate`, en la línea de [PersonProjectionJdbc]/`StudentLookupJdbc`: SQL
 * plano, sin `@Entity`, sobre la misma tabla `club_taxonomia.persona`.
 *
 * `club_id = ?` por parámetro en cada predicado, nunca igualdad fila-a-fila entre tablas — misma defensa anti-IDOR que
 * `GroupRepositoryJdbc`.
 */
@Repository
class StudentDirectoryJdbc(
    private val jdbc: JdbcTemplate,
) : StudentDirectory {
    @AuthScope(Scope.CLUB)
    override fun listByClub(
        clubId: ClubId,
        requiredTagValueIds: Set<TagValueId>,
    ): List<StudentSummary> =
        if (requiredTagValueIds.isEmpty()) {
            jdbc.query(LIST_ALL_SQL, { rs: ResultSet, _: Int -> toSummary(rs) }, clubId.value, clubId.value)
        } else {
            listFiltered(clubId, requiredTagValueIds)
        }

    /**
     * El array y el número exigido en el `HAVING` de [LIST_FILTERED_SQL] salen de la misma lista: así no pueden
     * divergir, mismo criterio que `PREVIEW_MEMBERS_SQL` en `GroupRepositoryJdbc`. `setArray` necesita la conexión, así
     * que este método va con `PreparedStatementSetter`, no con varargs.
     */
    private fun listFiltered(
        clubId: ClubId,
        requiredTagValueIds: Set<TagValueId>,
    ): List<StudentSummary> {
        val required = requiredTagValueIds.map { it.value }.toTypedArray()
        return jdbc.query(
            LIST_FILTERED_SQL,
            { statement: PreparedStatement ->
                statement.setObject(FILTERED_CUMPLEN_CLUB_PARAM, clubId.value)
                statement.setArray(FILTERED_TAGS_PARAM, statement.connection.createArrayOf("uuid", required))
                statement.setInt(FILTERED_COUNT_PARAM, required.size)
                statement.setObject(FILTERED_VALORES_CLUB_PARAM, clubId.value)
                statement.setObject(FILTERED_PERSONA_CLUB_PARAM, clubId.value)
            },
            { rs: ResultSet, _: Int -> toSummary(rs) },
        )
    }
}

// SQL a nivel de fichero, no en `companion object`: una propiedad privada del companion leída desde la clase genera un
// accesor sintético público que la malla anti-IDOR contaría como método del `@Repository` sin `@AuthScope`.

private fun toSummary(rs: ResultSet): StudentSummary =
    StudentSummary(
        id = PersonId.of(rs.getObject("id", UUID::class.java)),
        name = rs.getString("nombre"),
        email = rs.getString("email"),
        status = PersonStatus.valueOf(rs.getString("estado")),
        tagValueIds =
            (rs.getArray("valores").array as Array<*>)
                .filterIsInstance<UUID>()
                .mapTo(linkedSetOf()) { TagValueId.of(it) },
    )

/**
 * Todos los alumnos del club, sin filtro. `valores` es un subselect `array_agg` de **todos** los tags del alumno, no
 * solo los que interesarían a un filtro que aquí no existe -- la fila tiene que poder pintar todos sus chips.
 *
 * Orden de los 2 parámetros: club (subselect de valores), club (persona).
 */
private const val LIST_ALL_SQL =
    """
    SELECT p.id,
           p.nombre,
           p.email,
           p.estado,
           COALESCE(
               (SELECT array_agg(at.tag_value_id ORDER BY at.tag_value_id)
                FROM club_taxonomia.alumno_tag at
                WHERE at.club_id = ? AND at.alumno_id = p.id),
               '{}'::uuid[]
           ) AS valores
    FROM club_taxonomia.persona p
    WHERE p.club_id = ? AND p.rol = 'ALUMNO'
    ORDER BY p.nombre, p.id
    """

// Posiciones de los parámetros de LIST_FILTERED_SQL: el array de tags necesita `setArray` y la conexión, así que los
// cuatro se fijan a mano en vez de pasarse como varargs.
private const val FILTERED_CUMPLEN_CLUB_PARAM = 1
private const val FILTERED_TAGS_PARAM = 2
private const val FILTERED_COUNT_PARAM = 3
private const val FILTERED_VALORES_CLUB_PARAM = 4
private const val FILTERED_PERSONA_CLUB_PARAM = 5

/**
 * Alumnos del club que tienen **todos** los `requiredTagValueIds` (AND, ADR-0002 D3), con dos sentencias en vez de una
 * condicional: `tag_value_id = ANY ('{}')` es falso para toda fila, así que una consulta con el filtro siempre
 * presente daría lista vacía con el filtro vacío -- justo lo contrario de lo que necesita un listado, cuyo estado base
 * es la lista completa. Es la misma razón por la que este fichero duplica en vez de compartir CTEs, ya establecida en
 * `GroupRepositoryJdbc`.
 *
 * `at.club_id = ?` y `p.club_id = ?` ligan ambas tablas al club del llamador **por parámetro**, nunca por igualdad
 * fila-a-fila entre ellas.
 *
 * Un `tagValueId` desconocido o de otro club no rompe nada: simplemente no aparece en `alumno_tag` con ese club, así
 * que la CTE `cumplen` no encuentra a nadie y la respuesta sale vacía, sin error -- es un filtro de lectura, no una
 * validación de escritura como la del alta de un grupo.
 */
private const val LIST_FILTERED_SQL =
    """
    WITH cumplen AS (
        SELECT alumno_id
        FROM club_taxonomia.alumno_tag
        WHERE club_id = ? AND tag_value_id = ANY (?)
        GROUP BY alumno_id
        HAVING COUNT(DISTINCT tag_value_id) = ?
    )
    SELECT p.id,
           p.nombre,
           p.email,
           p.estado,
           COALESCE(
               (SELECT array_agg(at.tag_value_id ORDER BY at.tag_value_id)
                FROM club_taxonomia.alumno_tag at
                WHERE at.club_id = ? AND at.alumno_id = p.id),
               '{}'::uuid[]
           ) AS valores
    FROM club_taxonomia.persona p
    JOIN cumplen c ON c.alumno_id = p.id
    WHERE p.club_id = ? AND p.rol = 'ALUMNO'
    ORDER BY p.nombre, p.id
    """
