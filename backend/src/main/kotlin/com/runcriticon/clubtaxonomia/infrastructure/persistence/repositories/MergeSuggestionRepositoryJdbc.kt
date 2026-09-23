package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import arrow.core.getOrElse
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.MergeSuggestionRepository
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupName
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestion
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionOverview
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * Adaptador de [MergeSuggestionRepository] sobre `JdbcTemplate` -- sin `@Entity`, mismo motivo que
 * [GroupRepositoryJdbc]: es CRUD simple sobre una tabla propia, no un grafo de entidades Hibernate.
 *
 * Cada sentencia filtra por `club_id`, defensa en profundidad anti-IDOR igual que el resto del módulo.
 */
@Repository
class MergeSuggestionRepositoryJdbc(
    private val jdbc: JdbcTemplate,
) : MergeSuggestionRepository {
    @NoAuthScope(
        justificacion =
            "Invocado solo desde MergeSuggestionListener (recalculo asincrono de MembresiaDeGrupoCambiada): " +
                "un @ApplicationModuleListener corre en su propio hilo, sin Principal/SecurityContext. " +
                "@AuthScope(Scope.CLUB) aqui fallaba fail-closed en cada entrega real, dejando la publicacion del " +
                "evento sin completar. El club lo identifica el evento consumido, no una peticion HTTP.",
    )
    override fun upsert(
        clubId: ClubId,
        suggestion: MergeSuggestion,
    ) {
        jdbc.update(
            UPSERT_SQL,
            clubId.value,
            suggestion.groupIdA.value,
            suggestion.groupIdB.value,
            suggestion.type.name,
            Timestamp.from(suggestion.calculatedAt),
        )
    }

    @NoAuthScope(
        justificacion =
            "Mismo motivo que upsert: invocado solo desde MergeSuggestionListener, sin Principal.",
    )
    override fun delete(
        clubId: ClubId,
        groupIdA: GroupId,
        groupIdB: GroupId,
        type: MergeSuggestionType,
    ) {
        jdbc.update(DELETE_SQL, clubId.value, groupIdA.value, groupIdB.value, type.name)
    }

    @AuthScope(Scope.CLUB)
    override fun listActive(clubId: ClubId): List<MergeSuggestionOverview> =
        jdbc.query(LIST_ACTIVE_SQL, { rs: ResultSet, _: Int -> toOverview(rs) }, clubId.value)

    @AuthScope(Scope.CLUB)
    override fun dismiss(
        clubId: ClubId,
        groupIdA: GroupId,
        groupIdB: GroupId,
        type: MergeSuggestionType,
    ): Boolean = jdbc.update(DISMISS_SQL, clubId.value, groupIdA.value, groupIdB.value, type.name) > 0
}

private fun toOverview(rs: ResultSet): MergeSuggestionOverview =
    MergeSuggestionOverview(
        groupAId = GroupId.of(rs.getObject("grupo_id_a", UUID::class.java)),
        groupAName = nameOrGarbage(rs.getString("nombre_a")),
        groupBId = GroupId.of(rs.getObject("grupo_id_b", UUID::class.java)),
        groupBName = nameOrGarbage(rs.getString("nombre_b")),
        type = MergeSuggestionType.valueOf(rs.getString("tipo")),
        calculatedAt = rs.getTimestamp("calculado_en").toInstant(),
    )

/** Mismo criterio que `GroupRepositoryJdbc.toGroup`: un nombre inválido es basura de fuera de la aplicación. */
private fun nameOrGarbage(value: String): GroupName =
    GroupName.of(value).getOrElse { error("Nombre de grupo inválido en club_taxonomia.grupo") }

private const val UPSERT_SQL =
    """
    INSERT INTO club_taxonomia.sugerencia_fusion_grupo (club_id, grupo_id_a, grupo_id_b, tipo, calculado_en)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (club_id, grupo_id_a, grupo_id_b, tipo) DO UPDATE SET calculado_en = EXCLUDED.calculado_en
    """

private const val DELETE_SQL =
    """
    DELETE FROM club_taxonomia.sugerencia_fusion_grupo
    WHERE club_id = ? AND grupo_id_a = ? AND grupo_id_b = ? AND tipo = ?
    """

/**
 * `grupo` se une dos veces -- una por cada lado del par -- porque en `MICRO` ambos lados son el mismo grupo:
 * un `JOIN` único con `IN (grupo_id_a, grupo_id_b)` devolvería una sola fila de nombre en ese caso, no dos.
 */
private const val LIST_ACTIVE_SQL =
    """
    SELECT s.grupo_id_a, ga.nombre AS nombre_a, s.grupo_id_b, gb.nombre AS nombre_b, s.tipo, s.calculado_en
    FROM club_taxonomia.sugerencia_fusion_grupo s
    JOIN club_taxonomia.grupo ga ON ga.id = s.grupo_id_a AND ga.club_id = s.club_id
    JOIN club_taxonomia.grupo gb ON gb.id = s.grupo_id_b AND gb.club_id = s.club_id
    WHERE s.club_id = ? AND s.descartada_en IS NULL
    ORDER BY s.calculado_en DESC
    """

private const val DISMISS_SQL =
    """
    UPDATE club_taxonomia.sugerencia_fusion_grupo
    SET descartada_en = now()
    WHERE club_id = ? AND grupo_id_a = ? AND grupo_id_b = ? AND tipo = ? AND descartada_en IS NULL
    """
