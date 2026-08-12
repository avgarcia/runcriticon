package com.runcriticon.clubtaxonomia.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.CoachDirectory
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
 * `@Entity`, sobre la misma tabla `club_taxonomia.persona`.
 *
 * `club_id = ?` por parámetro, nunca igualdad fila-a-fila entre tablas — misma defensa anti-IDOR que
 * `StudentDirectoryJdbc`/`GroupRepositoryJdbc`.
 */
@Repository
class CoachDirectoryJdbc(
    private val jdbc: JdbcTemplate,
) : CoachDirectory {
    @AuthScope(Scope.CLUB)
    override fun listByClub(clubId: ClubId): List<CoachWorkload> =
        jdbc.query(LIST_ALL_SQL, { rs: ResultSet, _: Int -> toWorkload(rs) }, clubId.value)
}

// SQL a nivel de fichero, no en `companion object`: una propiedad privada del companion leída desde la clase genera
// un accesor sintético público que la malla anti-IDOR contaría como método del `@Repository` sin `@AuthScope`.

/**
 * `groups` sale siempre vacía y `totalStudents` siempre a 0: no existe todavía la asignación entrenador↔grupo
 * (LAL-93) de la que sacarlas. No es un `TODO`, es el estado correcto hasta que esa relación exista.
 */
private fun toWorkload(rs: ResultSet): CoachWorkload =
    CoachWorkload(
        id = PersonId.of(rs.getObject("id", UUID::class.java)),
        name = rs.getString("nombre"),
        email = rs.getString("email"),
        status = PersonStatus.valueOf(rs.getString("estado")),
        groups = emptyList(),
        totalStudents = 0,
    )

private const val LIST_ALL_SQL =
    """
    SELECT p.id, p.nombre, p.email, p.estado
    FROM club_taxonomia.persona p
    WHERE p.club_id = ? AND p.rol = 'ENTRENADOR'
    ORDER BY p.nombre, p.id
    """
