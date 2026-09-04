package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import arrow.core.getOrElse
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupCoach
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.group.GroupExclusion
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupMember
import com.runcriticon.clubtaxonomia.domain.group.GroupMemberOrigin
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.group.GroupMembership
import com.runcriticon.clubtaxonomia.domain.group.GroupName
import com.runcriticon.clubtaxonomia.domain.group.GroupSummary
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
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
    override fun findGroupIdsByAnyRequiredTagValue(
        clubId: ClubId,
        tagValueIds: Set<TagValueId>,
    ): Set<GroupId> {
        val values = tagValueIds.map { it.value }.toTypedArray()
        return jdbc
            .query(
                FIND_GROUPS_BY_TAG_VALUE_SQL,
                { statement: PreparedStatement ->
                    statement.setObject(1, clubId.value)
                    statement.setArray(2, statement.connection.createArrayOf("uuid", values))
                },
                { rs: ResultSet, _: Int -> GroupId.of(rs.getObject("grupo_id", UUID::class.java)) },
            ).toSet()
    }

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

    @AuthScope(Scope.CLUB)
    override fun findDetail(
        clubId: ClubId,
        groupId: GroupId,
    ): GroupDetail? {
        val group =
            jdbc
                .query(
                    FIND_GROUP_SQL,
                    { rs: ResultSet, _: Int -> toGroup(rs, clubId) },
                    clubId.value,
                    groupId.value,
                    clubId.value,
                ).firstOrNull() ?: return null
        val rows = jdbc.query(FIND_MEMBERSHIP_SQL, ::toMembershipRow, *membershipArgs(groupId, clubId))
        return GroupDetail(
            group = group,
            members =
                rows.filter { it.belongs }.map {
                    GroupMembership(
                        member = it.member,
                        origin = if (it.matchesFilter) GroupMemberOrigin.FILTER else GroupMemberOrigin.MANUAL_INCLUSION,
                        hasOverride = it.hasOverride,
                    )
                },
            exclusions =
                rows.filterNot { it.belongs }.map {
                    GroupExclusion(member = it.member, matchesFilter = it.matchesFilter)
                },
        )
    }

    @AuthScope(Scope.CLUB)
    override fun exists(
        clubId: ClubId,
        groupId: GroupId,
    ): Boolean = jdbc.queryForObject(EXISTS_GROUP_SQL, Boolean::class.java, groupId.value, clubId.value) ?: false

    @AuthScope(Scope.CLUB)
    override fun upsertOverride(
        clubId: ClubId,
        groupId: GroupId,
        studentId: PersonId,
        included: Boolean,
    ) {
        jdbc.update(UPSERT_OVERRIDE_SQL, clubId.value, studentId.value, included, groupId.value, clubId.value)
    }

    @AuthScope(Scope.CLUB)
    override fun deleteOverride(
        clubId: ClubId,
        groupId: GroupId,
        studentId: PersonId,
    ): Int = jdbc.update(DELETE_OVERRIDE_SQL, groupId.value, clubId.value, studentId.value)

    @AuthScope(Scope.CLUB)
    override fun findCoaches(
        clubId: ClubId,
        groupId: GroupId,
    ): List<GroupCoach> =
        jdbc.query(
            FIND_COACHES_SQL,
            { rs: ResultSet, _: Int -> toGroupCoach(rs) },
            groupId.value,
            clubId.value,
            clubId.value,
        )

    @AuthScope(Scope.CLUB)
    override fun assignCoach(
        clubId: ClubId,
        groupId: GroupId,
        coachId: PersonId,
    ) {
        jdbc.update(ASSIGN_COACH_SQL, clubId.value, coachId.value, groupId.value, clubId.value)
    }

    @AuthScope(Scope.CLUB)
    override fun unassignCoach(
        clubId: ClubId,
        groupId: GroupId,
        coachId: PersonId,
    ): Int = jdbc.update(UNASSIGN_COACH_SQL, groupId.value, clubId.value, coachId.value)
}

private fun toSummary(
    rs: ResultSet,
    clubId: ClubId,
): GroupSummary = GroupSummary(group = toGroup(rs, clubId), memberCount = rs.getInt("total"))

/**
 * Reconstruye el grupo desde su fila. Un nombre que no pasa las invariantes no es un error de negocio que devolver al
 * llamante: es basura en la propia tabla, escrita por fuera de la aplicación, y se trata como lo que es.
 */
private fun toGroup(
    rs: ResultSet,
    clubId: ClubId,
): Group {
    val name =
        GroupName
            .of(rs.getString("nombre"))
            .getOrElse { error("Nombre de grupo inválido en club_taxonomia.grupo") }
    val values =
        (rs.getArray("valores").array as Array<*>)
            .filterIsInstance<UUID>()
            .mapTo(linkedSetOf()) { TagValueId.of(it) }
    return Group(
        id = GroupId.of(rs.getObject("id", UUID::class.java)),
        clubId = clubId,
        name = name,
        requiredTagValueIds = values,
    )
}

/**
 * Fila cruda de [FIND_MEMBERSHIP_SQL]. Las dos ramas de la consulta —quien pertenece y quien está excluido a mano—
 * vienen en el mismo resultado y se separan aquí por [belongs]: así el origen de los miembros y el `matchesFilter` de
 * las exclusiones salen del mismo `cumple_filtro`, sin poder discrepar.
 */
private data class MembershipRow(
    val member: GroupMember,
    val belongs: Boolean,
    val matchesFilter: Boolean,
    val hasOverride: Boolean,
)

private fun toMembershipRow(
    rs: ResultSet,
    @Suppress("UNUSED_PARAMETER") rowNum: Int,
): MembershipRow =
    MembershipRow(
        member =
            GroupMember(
                id = PersonId.of(rs.getObject("id", UUID::class.java)),
                name = rs.getString("nombre"),
            ),
        belongs = rs.getBoolean("pertenece"),
        matchesFilter = rs.getBoolean("cumple_filtro"),
        hasOverride = rs.getBoolean("ajuste_manual"),
    )

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
 * **JOIN con `persona` y `rol = 'ALUMNO'`** (LAL-25, corrección de alcance): antes esta consulta no lo tenía,
 * a diferencia de [FIND_MEMBERSHIP_SQL]/[LIST_SUMMARIES_SQL], con el argumento de que su único consumidor era
 * el snapshot de publicación. Ese consumidor ya existe (el recálculo de membresía que alimenta
 * `planificacion.miembro_grupo`) y sin este JOIN un override `incluido = TRUE` sobre un entrenador, o sobre un
 * id sin fila en `persona`, saldría aquí pero es invisible en toda la UI -- publicar sobre esa discrepancia
 * sería un bug. Las cuatro resoluciones de este fichero quedan alineadas en ese criterio.
 *
 * Orden de los 10 parámetros posicionales, ver [resolveMembersArgs]: grupo, club, club, grupo, club, grupo,
 * club, grupo, club, club (persona).
 *
 * `internal`, no `private`: [com.runcriticon.clubtaxonomia.performance.GroupResolutionLoadTest] (LAL-95) necesita
 * pedir `EXPLAIN (ANALYZE, FORMAT JSON)` de esta consulta exacta -- duplicar el SQL en el test se desincronizaría
 * en silencio con la próxima revisión de esta query.
 */
internal const val RESOLVE_MEMBERS_SQL =
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
    ),
    miembros AS (
        SELECT alumno_id FROM cumplen_tags
        UNION
        SELECT alumno_id FROM incluidos
        EXCEPT
        SELECT alumno_id FROM excluidos
    )
    SELECT m.alumno_id
    FROM miembros m
    JOIN club_taxonomia.persona p ON p.id = m.alumno_id
    WHERE p.club_id = ? AND p.rol = 'ALUMNO'
    """

/** Construye los 10 argumentos posicionales de [RESOLVE_MEMBERS_SQL] en el orden exacto en que aparecen los `?`. */
internal fun resolveMembersArgs(
    groupId: GroupId,
    clubId: ClubId,
): Array<Any> {
    val group = groupId.value
    val club = clubId.value
    return arrayOf(group, club, club, group, club, group, club, group, club, club)
}

/**
 * Query inversa (LAL-25): grupos de `club_id` cuyo filtro usa alguno de los valores de `tagValueIds`. `= ANY (?)`
 * con `uuid[]`, mismo recurso que [PREVIEW_MEMBERS_SQL], no `IN (?, ?, …)`. La PK de `grupo_tag_requerido` es
 * `(grupo_id, tag_value_id)`, así que esta consulta necesita el índice aditivo `(club_id, tag_value_id)` de la
 * migración de este ticket -- sin él sería un escaneo completo de la tabla.
 */
private const val FIND_GROUPS_BY_TAG_VALUE_SQL =
    """
    SELECT DISTINCT grupo_id
    FROM club_taxonomia.grupo_tag_requerido
    WHERE club_id = ? AND tag_value_id = ANY (?)
    """

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
internal const val LIST_SUMMARIES_CLUB_PARAMS = 7

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
 * Es la tercera forma de resolver la membresía en este fichero y las cuatro difieren a propósito: la resolución de un
 * grupo guardado no mira `persona` (su consumidor es el snapshot de publicación, no una pantalla), la previsualización
 * no mira las excepciones manuales (todavía no hay grupo del que colgarlas), esta mira ambas cosas -- porque el número
 * de la lista tiene que ser el mismo que el usuario acaba de ver en la vista previa al construir el grupo -- y la del
 * detalle ([FIND_MEMBERSHIP_SQL]) añade además el motivo de cada pertenencia.
 *
 * `array_agg` lleva `ORDER BY` porque sin él el orden es indeterminado y la frase del filtro bailaría entre recargas.
 *
 * `LEFT JOIN` desde `grupos`: un grupo sin miembros sale con cero. Con un INNER desaparecería de la lista justo en el
 * estado que hay que enseñar. Y un grupo sin filtro no entra en `filtros`, así que solo suman sus inclusiones
 * manuales, sin caso especial en Kotlin.
 *
 * Sin índices nuevos: a la escala prevista (un par de cientos de grupos) el barrido secuencial de las tablas de filtro
 * y de excepciones gana al índice, y el JOIN caro contra `alumno_tag` ya está cubierto.
 *
 * `internal`, no `private`: ver la nota de [RESOLVE_MEMBERS_SQL] -- LAL-95 mide y pide `EXPLAIN` de esta consulta
 * exacta, la misma que afirma en este KDoc que el barrido secuencial gana al índice a esta escala.
 */
internal const val LIST_SUMMARIES_SQL =
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

/**
 * El grupo y su filtro, sin resolver membresía. Va aparte de [FIND_MEMBERSHIP_SQL] porque son dos preguntas: si el
 * grupo existe en este club —de la que sale el 404— y quién está dentro.
 *
 * Orden de los 3 parámetros: club (filtro), grupo, club (grupo).
 */
private const val FIND_GROUP_SQL =
    """
    SELECT g.id,
           g.nombre,
           COALESCE(
               (SELECT array_agg(tag_value_id ORDER BY tag_value_id)
                FROM club_taxonomia.grupo_tag_requerido
                WHERE grupo_id = g.id AND club_id = ?),
               '{}'::uuid[]
           ) AS valores
    FROM club_taxonomia.grupo g
    WHERE g.id = ? AND g.club_id = ?
    """

/**
 * Cuarta resolución de membresía del fichero, y difiere de las otras tres en que devuelve **por qué** está cada miembro
 * y además a los excluidos a mano, que no son miembros. Se duplica en vez de compartir las CTEs a propósito, como las
 * anteriores: es lo que permite auditar de un vistazo que cada predicado lleva su `club_id = ?` por parámetro.
 *
 * `LEFT JOIN cumplen_tags` es lo único nuevo respecto a la resolución canónica, y de él salen las dos respuestas:
 * `cumple_filtro` decide el origen de un miembro (cumple → filtro; no cumple → inclusión manual) y es el
 * `matchesFilter` de una exclusión. `LEFT JOIN incluidos` aporta `ajuste_manual`, que es otra pregunta: si hay
 * excepción guardada que se pueda quitar. Un alumno puede cumplir el filtro **y** tener inclusión manual; entonces sale
 * como miembro por filtro, con `ajuste_manual` a `TRUE`.
 *
 * **La regla implementada equivale a la del modelo de datos** —`está_en_grupo = (cumple ∧ ¬excluido) ∨ incluido`—
 * aunque el SQL calcule `(cumple ∨ incluido) ∧ ¬excluido`: las dos formas solo difieren cuando alguien está incluido y
 * excluido a la vez, y eso lo impide la clave primaria `(grupo_id, alumno_id)`. Si una migración futura historizara los
 * overrides y admitiera varias filas por par, esta equivalencia dejaría de valer.
 *
 * El JOIN con `persona` va **después** del `UNION`/`EXCEPT`, mismo motivo que en [LIST_SUMMARIES_SQL]: dentro de
 * `cumplen_tags`, una excepción sobre quien no es alumno del club se saltaría el filtro de rol. Una excepción sobre un
 * entrenador o sobre un id sin fila en la proyección simplemente no aparece.
 *
 * Orden de los 11 parámetros, ver [membershipArgs]: grupo, club, club, grupo, club, grupo, club, grupo, club, club,
 * club.
 */
private const val FIND_MEMBERSHIP_SQL =
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
    ),
    miembros AS (
        SELECT alumno_id FROM cumplen_tags
        UNION
        SELECT alumno_id FROM incluidos
        EXCEPT
        SELECT alumno_id FROM excluidos
    )
    SELECT p.id,
           p.nombre,
           TRUE                          AS pertenece,
           (c.alumno_id IS NOT NULL)     AS cumple_filtro,
           (i.alumno_id IS NOT NULL)     AS ajuste_manual
    FROM miembros m
    JOIN club_taxonomia.persona p ON p.id = m.alumno_id
    LEFT JOIN cumplen_tags c ON c.alumno_id = m.alumno_id
    LEFT JOIN incluidos i ON i.alumno_id = m.alumno_id
    WHERE p.club_id = ? AND p.rol = 'ALUMNO'
    UNION ALL
    SELECT p.id,
           p.nombre,
           FALSE                         AS pertenece,
           (c.alumno_id IS NOT NULL)     AS cumple_filtro,
           TRUE                          AS ajuste_manual
    FROM excluidos e
    JOIN club_taxonomia.persona p ON p.id = e.alumno_id
    LEFT JOIN cumplen_tags c ON c.alumno_id = e.alumno_id
    WHERE p.club_id = ? AND p.rol = 'ALUMNO'
    ORDER BY pertenece DESC, nombre, id
    """

/** Construye los 11 argumentos posicionales de [FIND_MEMBERSHIP_SQL] en el orden exacto en que aparecen los `?`. */
private fun membershipArgs(
    groupId: GroupId,
    clubId: ClubId,
): Array<Any> {
    val group = groupId.value
    val club = clubId.value
    return arrayOf(group, club, club, group, club, group, club, group, club, club, club)
}

private const val EXISTS_GROUP_SQL =
    "SELECT EXISTS (SELECT 1 FROM club_taxonomia.grupo WHERE id = ? AND club_id = ?)"

/**
 * `INSERT ... SELECT` en vez de `VALUES`: el `WHERE g.club_id = ?` hace que un grupo de otro club no escriba ninguna
 * fila, en vez de crear una excepción huérfana. El caso de uso ya lo ha cortado antes con su comprobación de
 * pertenencia; esto es la segunda línea, para que no dependa de que el llamante se acuerde.
 *
 * El `ON CONFLICT ... DO UPDATE` da las dos propiedades que necesita el PUT: repetir la llamada deja el mismo estado, y
 * el sentido contrario voltea la excepción sin borrarla antes. `club_id` no se reescribe -- la clave primaria ya fija
 * el grupo, y el club de un grupo no cambia.
 *
 * Orden de los 5 parámetros: club (fila), alumno, incluido, grupo, club (guarda).
 */
private const val UPSERT_OVERRIDE_SQL =
    """
    INSERT INTO club_taxonomia.grupo_alumno_override (grupo_id, club_id, alumno_id, incluido)
    SELECT g.id, ?, ?, ?
    FROM club_taxonomia.grupo g
    WHERE g.id = ? AND g.club_id = ?
    ON CONFLICT (grupo_id, alumno_id) DO UPDATE SET incluido = EXCLUDED.incluido
    """

private const val DELETE_OVERRIDE_SQL =
    """
    DELETE FROM club_taxonomia.grupo_alumno_override
    WHERE grupo_id = ? AND club_id = ? AND alumno_id = ?
    """

private fun toGroupCoach(rs: ResultSet): GroupCoach =
    GroupCoach(
        id = PersonId.of(rs.getObject("id", UUID::class.java)),
        name = rs.getString("nombre"),
        email = rs.getString("email"),
        status = PersonStatus.valueOf(rs.getString("estado")),
    )

/**
 * `JOIN persona`, no un `SELECT entrenador_id` suelto: la fila necesita nombre, email y estado, que no viven en
 * `grupo_entrenador`. `rol = 'ENTRENADOR'` es un tercer guardián además de los dos `club_id = ?` -- una asignación
 * sobre alguien que dejó de ser entrenador (imposible hoy, sin ticket que reclasifique un rol, pero barato de
 * cerrar) no aparecería con el rol equivocado.
 *
 * Orden de los 3 parámetros: grupo, club (`grupo_entrenador`), club (`persona`).
 */
private const val FIND_COACHES_SQL =
    """
    SELECT p.id, p.nombre, p.email, p.estado
    FROM club_taxonomia.grupo_entrenador ge
    JOIN club_taxonomia.persona p ON p.id = ge.entrenador_id
    WHERE ge.grupo_id = ? AND ge.club_id = ? AND p.club_id = ? AND p.rol = 'ENTRENADOR'
    ORDER BY p.nombre, p.id
    """

/**
 * `INSERT ... SELECT`, mismo motivo que [UPSERT_OVERRIDE_SQL]: el `WHERE g.club_id = ?` es la segunda línea de
 * defensa anti-IDOR, para que un grupo de otro club no escriba ninguna fila aunque el caso de uso ya lo haya
 * cortado antes.
 *
 * `ON CONFLICT DO NOTHING`, no `DO UPDATE`: a diferencia de un override (`incluido` puede voltear), una asignación
 * no tiene ningún atributo que reescribir -- están asignados o no lo están.
 *
 * Orden de los 4 parámetros: club (fila), entrenador, grupo, club (guarda).
 */
private const val ASSIGN_COACH_SQL =
    """
    INSERT INTO club_taxonomia.grupo_entrenador (grupo_id, club_id, entrenador_id)
    SELECT g.id, ?, ?
    FROM club_taxonomia.grupo g
    WHERE g.id = ? AND g.club_id = ?
    ON CONFLICT (grupo_id, entrenador_id) DO NOTHING
    """

private const val UNASSIGN_COACH_SQL =
    """
    DELETE FROM club_taxonomia.grupo_entrenador
    WHERE grupo_id = ? AND club_id = ? AND entrenador_id = ?
    """
