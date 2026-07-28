package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.clubtaxonomia.infrastructure.persistence.entities.TagKeyEntity
import com.runcriticon.clubtaxonomia.infrastructure.persistence.entities.TagValueEntity
import com.runcriticon.clubtaxonomia.infrastructure.persistence.mappers.TaxonomyMapper
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Adaptador del puerto [TaxonomyRepository] sobre Spring Data. Es el `@Repository` que ve la malla anti-IDOR: los dos
 * métodos públicos declaran `@AuthScope(Scope.CLUB)` y reciben el `clubId` en la firma, que es a la vez el filtro de
 * sus consultas y lo que `AuthScopeEnforcementAspect` contrasta contra el club del principal.
 *
 * **Escritura del agregado completo.** [save] recibe la taxonomía entera ya mutada, la contrasta con las filas del
 * club y aplica los cambios en cuatro fases con `flush()` entre ellas. El orden importa por los índices únicos
 * parciales `tag_key_club_nombre_uk` / `tag_value_key_nombre_uk`, que solo cuentan las filas activas: si una misma
 * escritura archivara un nombre y lo reutilizara, escribir primero la reutilización chocaría contra la fila que aún
 * no se ha archivado. Las fases son, por tanto:
 *
 *  1. **Bajas** de filas ausentes del agregado. Defensiva: hoy ningún caso de uso las produce —el dominio solo
 *     archiva (soft-delete), nunca elimina— pero deja el adaptador correcto si algún día se añade una eliminación.
 *  2. **Archivados**, que liberan el nombre en el índice parcial.
 *  3. **Resto de cambios** sobre filas existentes (renombrados, reactivaciones, metadata), que lo ocupan.
 *  4. **Altas**, hijos después de padres por la FK `tag_value.tag_key_id`.
 *
 * Limitación conocida y aceptada: un intercambio de nombres entre dos elementos activos en una sola escritura
 * (`A→B` y `B→A`) seguiría chocando. Ninguna mutación de `Taxonomy` puede producirlo —cada caso de uso aplica un solo
 * cambio— y resolverlo exigiría un nombre temporal o constraints diferibles.
 *
 * Las altas usan `EntityManager.persist` en vez de `JpaRepository.save`: con id asignado, `save` deduce que la
 * entidad no es nueva y hace `merge`, que añade un `SELECT` por fila antes del `INSERT`.
 */
@Repository
class TaxonomyRepositoryImpl(
    private val tagKeys: TagKeyEntityRepository,
    private val tagValues: TagValueEntityRepository,
    private val entityManager: EntityManager,
) : TaxonomyRepository {
    @AuthScope(Scope.CLUB)
    override fun findByClub(clubId: ClubId): Taxonomy =
        TaxonomyMapper.toDomain(
            clubId = clubId,
            keys = tagKeys.findAllByClubId(clubId.value),
            values = tagValues.findAllByClubId(clubId.value),
        )

    @AuthScope(Scope.CLUB)
    override fun save(
        clubId: ClubId,
        taxonomy: Taxonomy,
    ) {
        val existing = load(clubId)
        val incoming = flatten(taxonomy)

        deleteOrphans(existing, incoming)
        releaseArchivedNames(existing, incoming)
        applyChangesToExisting(existing, incoming)
        insertNew(existing, incoming, clubId)
    }

    /** Filas actuales del club, gestionadas por el contexto de persistencia: mutarlas es la vía de actualización. */
    private fun load(clubId: ClubId): ExistingRows =
        ExistingRows(
            keys = tagKeys.findAllByClubId(clubId.value).associateBy { it.id },
            values = tagValues.findAllByClubId(clubId.value).associateBy { it.id },
        )

    /** Aplana el árbol del agregado a filas, conservando de qué eje cuelga cada valor. */
    private fun flatten(taxonomy: Taxonomy): IncomingRows =
        IncomingRows(
            keys = taxonomy.keys,
            values = taxonomy.keys.flatMap { key -> key.values.map { key.id to it } },
        )

    private fun deleteOrphans(
        existing: ExistingRows,
        incoming: IncomingRows,
    ) {
        val keptKeys = incoming.keys.mapTo(mutableSetOf()) { it.id.value }
        val keptValues = incoming.values.mapTo(mutableSetOf()) { it.second.id.value }
        val orphanValues = existing.values.filterKeys { it !in keptValues }.values
        val orphanKeys = existing.keys.filterKeys { it !in keptKeys }.values
        if (orphanValues.isEmpty() && orphanKeys.isEmpty()) return

        // Hijos antes que padres: la FK tag_value.tag_key_id no la conoce Hibernate (columna plana, sin asociación),
        // así que el orden lo impone este flush, no el ActionQueue.
        tagValues.deleteAll(orphanValues)
        entityManager.flush()
        tagKeys.deleteAll(orphanKeys)
        entityManager.flush()
    }

    private fun releaseArchivedNames(
        existing: ExistingRows,
        incoming: IncomingRows,
    ) {
        var touched = false
        incoming.keys.forEach { key ->
            val row = existing.keys[key.id.value] ?: return@forEach
            if (row.archivedAt == null && key.archivedAt != null) {
                row.archivedAt = key.archivedAt
                touched = true
            }
        }
        incoming.values.forEach { (_, value) ->
            val row = existing.values[value.id.value] ?: return@forEach
            if (row.archivedAt == null && value.archivedAt != null) {
                row.archivedAt = value.archivedAt
                touched = true
            }
        }
        if (touched) entityManager.flush()
    }

    private fun applyChangesToExisting(
        existing: ExistingRows,
        incoming: IncomingRows,
    ) {
        incoming.keys.forEach { key ->
            val row = existing.keys[key.id.value] ?: return@forEach
            row.name = key.label.value
            row.archivedAt = key.archivedAt
        }
        incoming.values.forEach { (keyId, value) ->
            val row = existing.values[value.id.value] ?: return@forEach
            row.tagKeyId = keyId.value
            row.name = value.label.value
            row.metadata = value.metadata
            row.archivedAt = value.archivedAt
        }
        entityManager.flush()
    }

    private fun insertNew(
        existing: ExistingRows,
        incoming: IncomingRows,
        clubId: ClubId,
    ) {
        val now = Instant.now()
        val newKeys = incoming.keys.filter { it.id.value !in existing.keys }
        if (newKeys.isNotEmpty()) {
            newKeys.forEach { entityManager.persist(TaxonomyMapper.toEntity(it, clubId, now)) }
            // Padres antes que hijos, por la FK tag_value.tag_key_id.
            entityManager.flush()
        }
        val newValues = incoming.values.filter { it.second.id.value !in existing.values }
        if (newValues.isNotEmpty()) {
            newValues.forEach { (keyId, value) ->
                entityManager.persist(TaxonomyMapper.toEntity(value, keyId, clubId, now))
            }
            entityManager.flush()
        }
    }
}

/** Filas del club ya presentes en BD, indexadas por id. */
private class ExistingRows(
    val keys: Map<UUID, TagKeyEntity>,
    val values: Map<UUID, TagValueEntity>,
)

/** Estado que el agregado quiere dejar en BD, aplanado a filas. */
private class IncomingRows(
    val keys: List<TagKey>,
    val values: List<Pair<TagKeyId, TagValue>>,
)
