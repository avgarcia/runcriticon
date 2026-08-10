package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import arrow.core.getOrElse
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupMember
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.group.GroupName
import com.runcriticon.clubtaxonomia.domain.group.GroupSummary
import com.runcriticon.clubtaxonomia.domain.person.PersonId
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

    @AuthScope(Scope.CLUB)
    override fun previewMembers(
        clubId: ClubId,
        requiredTagValueIds: Set<TagValueId>,
    ): GroupMembers {
        // El array y el número exigido salen de la misma lista: así no pueden divergir.
        val required = requiredTagValueIds.map { it.value }.toTypedArray()
        val members =
            jdbc.query(
                PREVIEW_MEMBERS_SQL,
                { statement: PreparedStatement ->
                    statement.setObject(PREVIEW_CLUB_TAGS_PARAM, clubId.value)
                    statement.setArray(PREVIEW_TAGS_PARAM, statement.connection.createArrayOf("uuid", required))
                    statement.setInt(PREVIEW_COUNT_PARAM, required.size)
                    statement.setObject(PREVIEW_CLUB_PERSONA_PARAM, clubId.value)
                },
                { rs: ResultSet, _: Int ->
                    GroupMember(id = PersonId.of(rs.getObject("id", UUID::class.java)), name = rs.getString("nombre"))
                },
            )
        return GroupMembers(members)
    }

    @AuthScope(Scope.CLUB)
    override fun listSummaries(clubId: ClubId): List<GroupSummary> =
        jdbc.query(
            LIST_SUMMARIES_SQL,
            { rs: ResultSet, _: Int -> toSummary(rs, clubId) },
            *Array<Any>(LIST_SUMMARIES_CLUB_PARAMS) { clubId.value },
        )
}

/**
 * Reconstruye el grupo desde su fila. Un nombre que no pasa las invariantes no es un error de negocio que devolver al
 * llamante: es basura en la propia tabla, escrita por fuera de la aplicación, y se trata como lo que es.
 */
private fun toSummary(
    rs: ResultSet,
    clubId: ClubId,
): GroupSummary {
    val name =
        GroupName
            .of(rs.getString("nombre"))
            .getOrElse { error("Nombre de grupo inválido en club_taxonomia.grupo") }
    val values =
        (rs.getArray("valores").array as Array<*>)
            .filterIsInstance<UUID>()
            .mapTo(linkedSetOf()) { TagValueId.of(it) }
    val group =
        Group(
            id = GroupId.of(rs.getObject("id", UUID::class.java)),
            clubId = clubId,
            name = name,
            requiredTagValueIds = values,
        )
    return GroupSummary(group = group, memberCount = rs.getInt("total"))
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

// Posiciones de los parámetros de PREVIEW_MEMBERS_SQL: el array de tags necesita `setArray` y la conexión para
// construirlo, así que los cuatro se fijan a mano en vez de pasarse como varargs.
private const val PREVIEW_CLUB_TAGS_PARAM = 1
private const val PREVIEW_TAGS_PARAM = 2
private const val PREVIEW_COUNT_PARAM = 3
private const val PREVIEW_CLUB_PERSONA_PARAM = 4

/**
 * Variante sin grupo del SQL canónico de resolución: la CTE `cumplen_tags` recibe el filtro por parámetro en vez de
 * leerlo de `grupo_tag_requerido`, y no hay `incluidos`/`excluidos` porque las excepciones manuales cuelgan de un
 * grupo que aquí todavía no existe.
 *
 * `at.club_id = ?` y `p.club_id = ?` ligan ambas tablas al club del llamador **por parámetro**, nunca por igualdad
 * fila-a-fila entre ellas: dos filas mal etiquetadas de forma consistente entre sí se colarían.
 *
 * `= ANY (?)` con un `uuid[]` -- mismo recurso que `StudentTagRepositoryJdbc` -- en vez de un `IN (?, ?, ...)`
 * construido según el tamaño del conjunto: una sola entrada en la caché de planes sea cual sea el número de tags, y
 * ni un trozo de SQL armado con strings.
 *
 * **Filtro vacío devuelve vacío, sin caso especial**: `tag_value_id = ANY ('{}')` es falso para toda fila, así que no
 * se forma ningún grupo y el `HAVING` ni llega a evaluarse. Es el mismo resultado que da la resolución de un grupo
 * guardado sin tags requeridos. No cortocircuitar antes del SQL: dejaría la semántica definida en dos sitios.
 *
 * El JOIN con `persona` es INNER a propósito: la respuesta necesita el nombre, y una fila de `alumno_tag` sin persona
 * es una inconsistencia -- clasificar exige que la persona exista y el borrado se lleva ambas -- no un miembro que
 * haya que devolver anónimo. Aporta además el filtro `rol = 'ALUMNO'` y un segundo guardián de `club_id`. Cuando el
 * listado de miembros de un grupo ya guardado devuelva nombres tendrá que usar este mismo JOIN, o las dos listas
 * discreparán.
 *
 * **No se filtra por `estado`**: un alumno invitado y ya clasificado pertenece al grupo igual que uno activo, y la
 * resolución del grupo guardado tampoco mira el estado.
 *
 * El desempate por `id` en el `ORDER BY` es lo que hace determinista el orden entre nombres repetidos.
 *
 * Orden de los 4 parámetros: club (alumno_tag), array de tag values, número de tags exigidos, club (persona).
 */
private const val PREVIEW_MEMBERS_SQL =
    """
    WITH cumplen_tags AS (
        SELECT at.alumno_id
        FROM club_taxonomia.alumno_tag at
        WHERE at.club_id = ? AND at.tag_value_id = ANY (?)
        GROUP BY at.alumno_id
        HAVING COUNT(DISTINCT at.tag_value_id) = ?
    )
    SELECT p.id, p.nombre
    FROM cumplen_tags c
    JOIN club_taxonomia.persona p ON p.id = c.alumno_id
    WHERE p.club_id = ? AND p.rol = 'ALUMNO'
    ORDER BY p.nombre, p.id
    """

/** Los siete `?` de [LIST_SUMMARIES_SQL] reciben todos el mismo club, así que no hace falta orden posicional. */
private const val LIST_SUMMARIES_CLUB_PARAMS = 7

/**
 * Todos los grupos del club con su filtro y su recuento de miembros, en **una sola consulta**: resolver la membresía
 * grupo a grupo serían tantas consultas como grupos, y la pantalla los pinta todos de golpe.
 *
 * Cada predicado liga su tabla al club **por parámetro**, nunca por igualdad fila-a-fila entre tablas -- misma guardia
 * anti-IDOR que las otras dos consultas de este fichero.
 *
 * El JOIN con `persona` va **sobre `miembros`, después del `UNION`/`EXCEPT`**, no dentro de `cumplen_tags`: ahí dentro,
 * una inclusión manual sobre alguien que no es alumno se saltaría el filtro de rol e inflaría el contador.
 *
 * Es la tercera forma de resolver la membresía en este fichero y las tres difieren a propósito: la resolución de un
 * grupo guardado no mira `persona` (su consumidor es el snapshot de publicación, no una pantalla), la previsualización
 * no mira las excepciones manuales (todavía no hay grupo del que colgarlas), y esta mira ambas cosas -- porque el
 * número de la lista tiene que ser el mismo que el usuario acaba de ver en la vista previa al construir el grupo.
 *
 * `array_agg` lleva `ORDER BY` porque sin él el orden es indeterminado y la frase del filtro bailaría entre recargas.
 *
 * `LEFT JOIN` desde `grupos`: un grupo sin miembros sale con cero. Con un INNER desaparecería de la lista justo en el
 * estado que hay que enseñar. Y un grupo sin filtro no entra en `filtros`, así que solo suman sus inclusiones
 * manuales, sin caso especial en Kotlin.
 *
 * Sin índices nuevos: a la escala prevista (un par de cientos de grupos) el barrido secuencial de las tablas de filtro
 * y de excepciones gana al índice, y el JOIN caro contra `alumno_tag` ya está cubierto.
 */
private const val LIST_SUMMARIES_SQL =
    """
    WITH grupos AS (
        SELECT id, nombre FROM club_taxonomia.grupo WHERE club_id = ?
    ),
    filtros AS (
        SELECT grupo_id,
               array_agg(tag_value_id ORDER BY tag_value_id) AS valores,
               COUNT(*)                                      AS exigidos
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
    totales AS (
        SELECT m.grupo_id, COUNT(*) AS total
        FROM miembros m
        JOIN club_taxonomia.persona p ON p.id = m.alumno_id
        WHERE p.club_id = ? AND p.rol = 'ALUMNO'
        GROUP BY m.grupo_id
    )
    SELECT g.id,
           g.nombre,
           COALESCE(f.valores, '{}'::uuid[]) AS valores,
           COALESCE(t.total, 0)              AS total
    FROM grupos g
    LEFT JOIN filtros f ON f.grupo_id = g.id
    LEFT JOIN totales t ON t.grupo_id = g.id
    ORDER BY g.nombre, g.id
    """
