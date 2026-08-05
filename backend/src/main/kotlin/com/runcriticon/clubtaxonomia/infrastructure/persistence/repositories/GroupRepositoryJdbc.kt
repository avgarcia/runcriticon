package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adaptador de [GroupRepository] sobre `JdbcTemplate`.
 *
 * **Sin `@Entity`**, mismo motivo que `StudentTagRepositoryJdbc`: la resolución de membresía es SQL relacional
 * puro con CTEs (ADR-0002 D3+D4) que no tiene sentido modelar como grafo de entidades Hibernate, y `alumno_tag` --
 * de la que depende la resolución -- ya se gestiona fuera de JPA por el mismo motivo.
 *
 * Todas las sentencias filtran por `club_id` en cada tabla referenciada, no solo por `grupo_id`: defensa en
 * profundidad anti-IDOR ante un `groupId` de otro club, sin indirección de subquery contra `grupo`.
 */
@Repository
class GroupRepositoryJdbc(
    private val jdbc: JdbcTemplate,
) : GroupRepository {
    @AuthScope(Scope.CLUB)
    override fun save(
        clubId: ClubId,
        group: Group,
    ) {
        jdbc.update(INSERT_GROUP_SQL, group.id.value, clubId.value, group.name.value)
        if (group.requiredTagValueIds.isEmpty()) return
        jdbc.batchUpdate(
            INSERT_REQUIRED_TAG_SQL,
            group.requiredTagValueIds.map { arrayOf<Any>(group.id.value, clubId.value, it.value) },
        )
    }

    @AuthScope(Scope.CLUB)
    override fun resolveMembers(
        clubId: ClubId,
        groupId: GroupId,
    ): Set<PersonId> =
        jdbc
            .queryForList(RESOLVE_MEMBERS_SQL, UUID::class.java, *resolveMembersArgs(groupId, clubId))
            .filterNotNull()
            .mapTo(mutableSetOf()) { PersonId.of(it) }
}

// SQL a nivel de fichero: en un `companion object` generaría accesores sintéticos públicos que la malla anti-IDOR
// contaría como métodos del `@Repository` sin `@AuthScope`.

private const val INSERT_GROUP_SQL =
    "INSERT INTO club_taxonomia.grupo (id, club_id, nombre) VALUES (?, ?, ?)"

private const val INSERT_REQUIRED_TAG_SQL =
    "INSERT INTO club_taxonomia.grupo_tag_requerido (grupo_id, club_id, tag_value_id) VALUES (?, ?, ?)"

/**
 * SQL canónico de resolución de membresía (ADR-0002 D3+D4, `docs/adr/0002-modelo-de-datos-tags.md` líneas
 * 166-197), con `club_id = ?` añadido a cada predicado como defensa en profundidad: si [GroupId] no pertenece a
 * [ClubId], ningún predicado casa y el resultado es el conjunto vacío, sin lanzar error de dominio.
 *
 * `at.club_id = ?` liga `alumno_tag` al club del llamador **por parámetro**, no por igualdad fila-a-fila contra
 * `gtr.club_id` (`ON ... AND at.club_id = gtr.club_id`) -- esa forma dejaría pasar dos filas mal etiquetadas de
 * forma consistente entre sí. Fijar el mismo valor de club por parámetro en las tres CTEs cierra ese hueco.
 *
 * Orden de los 9 parámetros posicionales, ver [resolveMembersArgs]: grupo, club, club, grupo, club, grupo, club,
 * grupo, club.
 */
private const val RESOLVE_MEMBERS_SQL =
    """
    WITH cumplen_tags AS (
        SELECT at.alumno_id
        FROM club_taxonomia.alumno_tag at
        JOIN club_taxonomia.grupo_tag_requerido gtr ON at.tag_value_id = gtr.tag_value_id
        WHERE gtr.grupo_id = ? AND gtr.club_id = ? AND at.club_id = ?
        GROUP BY at.alumno_id
        HAVING COUNT(DISTINCT gtr.tag_value_id) = (
            SELECT COUNT(*) FROM club_taxonomia.grupo_tag_requerido WHERE grupo_id = ? AND club_id = ?
        )
    ),
    incluidos AS (
        SELECT alumno_id FROM club_taxonomia.grupo_alumno_override
        WHERE grupo_id = ? AND club_id = ? AND incluido = TRUE
    ),
    excluidos AS (
        SELECT alumno_id FROM club_taxonomia.grupo_alumno_override
        WHERE grupo_id = ? AND club_id = ? AND incluido = FALSE
    )
    SELECT alumno_id FROM cumplen_tags
    UNION
    SELECT alumno_id FROM incluidos
    EXCEPT
    SELECT alumno_id FROM excluidos
    """

/** Construye los 9 argumentos posicionales de [RESOLVE_MEMBERS_SQL] en el orden exacto en que aparecen los `?`. */
private fun resolveMembersArgs(
    groupId: GroupId,
    clubId: ClubId,
): Array<Any> {
    val group = groupId.value
    val club = clubId.value
    return arrayOf(group, club, club, group, club, group, club, group, club)
}
