package com.runcriticon.clubtaxonomia.domain.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant

/**
 * Raíz del agregado **taxonomía** de un club: el conjunto de sus [TagKey], cada uno con sus [TagValue].
 *
 * Dueña de dos invariantes que no dependen de la BD (dominio puro):
 *  - **Unicidad**: no puede haber dos `TagKey` activos con el mismo nombre normalizado en el club, ni dos `TagValue`
 *    activos con el mismo valor normalizado dentro de una misma key. La comparación ignora mayúsculas, acentos y
 *    espacios de los extremos ([TagLabel.normalized]); los archivados no cuentan.
 *  - **Archivado**: soft-delete sin cascada. Archivar una key la oculta a ella y, vía [assignableValues], a toda su
 *    rama; los valores conservan su propio `archivedAt`. Un nombre archivado se libera para reutilizarlo.
 *
 * Toda mutación devuelve `Either<ClubTaxonomiaError, TaxonomyUpdate<T>>`: el dominio nunca lanza excepción de negocio.
 *
 * **Fuera de esta raíz** (por diseño, no por olvido): la regla «no archivar una etiqueta requerida por un grupo vivo»
 * vive en el caso de uso cuando exista el agregado `Grupo` — la taxonomía no conoce grupos.
 */
data class Taxonomy(
    val clubId: ClubId,
    val keys: List<TagKey>,
) {
    // --- mutaciones de TagKey -----------------------------------------------------------------------------------

    fun addKey(
        rawLabel: String,
        id: TagKeyId = TagKeyId.new(),
    ): Either<ClubTaxonomiaError, TaxonomyUpdate<TagKey>> =
        either {
            val label = TagLabel.forKey(rawLabel).bind()
            ensure(!hasActiveKeyLabel(label.normalized, excluding = null)) {
                ClubTaxonomiaError.DuplicateLabel(TagKey.FIELD, label.value)
            }
            // El id lo puede suministrar el llamador (seed, tests): sin esto, dos keys con el mismo id convivirían y
            // replaceKey las mutaría a la vez.
            ensure(findKey(id) == null) { ClubTaxonomiaError.Conflict("duplicate_id") }
            val created = TagKey(id = id, clubId = clubId, label = label, archivedAt = null, values = emptyList())
            TaxonomyUpdate(copy(keys = keys + created), created)
        }

    fun renameKey(
        keyId: TagKeyId,
        rawLabel: String,
    ): Either<ClubTaxonomiaError, TaxonomyUpdate<TagKey>> =
        either {
            val key = findKey(keyId)
            ensureNotNull(key) { ClubTaxonomiaError.TagKeyNotFound }
            val label = TagLabel.forKey(rawLabel).bind()
            // Solo una key activa compite por la unicidad; una archivada puede tener cualquier nombre (índice parcial).
            if (key.isActive) {
                ensure(!hasActiveKeyLabel(label.normalized, excluding = keyId)) {
                    ClubTaxonomiaError.DuplicateLabel(TagKey.FIELD, label.value)
                }
            }
            val renamed = key.copy(label = label)
            TaxonomyUpdate(replaceKey(renamed), renamed)
        }

    /**
     * Archiva una key (soft-delete). Idempotente: si ya estaba archivada conserva su `archivedAt` original.
     *
     * Precondición NO comprobada aquí: no debe archivarse una key requerida por un grupo vivo. Esa regla la aplica el
     * caso de uso cuando exista el agregado `Grupo`; la taxonomía no conoce grupos.
     */
    fun archiveKey(
        keyId: TagKeyId,
        at: Instant,
    ): Either<ClubTaxonomiaError, TaxonomyUpdate<TagKey>> =
        either {
            val key = findKey(keyId)
            ensureNotNull(key) { ClubTaxonomiaError.TagKeyNotFound }
            if (!key.isActive) return@either TaxonomyUpdate(this@Taxonomy, key)
            val archived = key.copy(archivedAt = at)
            TaxonomyUpdate(replaceKey(archived), archived)
        }

    fun reactivateKey(keyId: TagKeyId): Either<ClubTaxonomiaError, TaxonomyUpdate<TagKey>> =
        either {
            val key = findKey(keyId)
            ensureNotNull(key) { ClubTaxonomiaError.TagKeyNotFound }
            if (key.isActive) return@either TaxonomyUpdate(this@Taxonomy, key)
            // Reactivar puede chocar si mientras estaba archivada se creó otra key activa con el mismo nombre.
            ensure(!hasActiveKeyLabel(key.label.normalized, excluding = keyId)) {
                ClubTaxonomiaError.DuplicateLabel(TagKey.FIELD, key.label.value)
            }
            val reactivated = key.copy(archivedAt = null)
            TaxonomyUpdate(replaceKey(reactivated), reactivated)
        }

    // --- mutaciones de TagValue ---------------------------------------------------------------------------------

    fun addValue(
        keyId: TagKeyId,
        rawLabel: String,
        metadata: TagValueMetadata = TagValueMetadata.Empty,
        id: TagValueId = TagValueId.new(),
    ): Either<ClubTaxonomiaError, TaxonomyUpdate<TagValue>> =
        either {
            val key = findKey(keyId)
            ensureNotNull(key) { ClubTaxonomiaError.TagKeyNotFound }
            ensure(key.isActive) { ClubTaxonomiaError.Conflict("tag_key_archived") }
            val label = TagLabel.forValue(rawLabel).bind()
            ensure(!hasActiveValueLabel(key, label.normalized, excluding = null)) {
                ClubTaxonomiaError.DuplicateLabel(TagValue.FIELD, label.value)
            }
            ensure(findValue(id) == null) { ClubTaxonomiaError.Conflict("duplicate_id") }
            val created = TagValue(id = id, label = label, metadata = metadata, archivedAt = null)
            val updatedKey = key.copy(values = key.values + created)
            TaxonomyUpdate(replaceKey(updatedKey), created)
        }

    fun renameValue(
        valueId: TagValueId,
        rawLabel: String,
    ): Either<ClubTaxonomiaError, TaxonomyUpdate<TagValue>> =
        either {
            val located = findValueWithKey(valueId)
            ensureNotNull(located) { ClubTaxonomiaError.TagValueNotFound }
            val (key, value) = located
            val label = TagLabel.forValue(rawLabel).bind()
            if (value.isActive) {
                ensure(!hasActiveValueLabel(key, label.normalized, excluding = valueId)) {
                    ClubTaxonomiaError.DuplicateLabel(TagValue.FIELD, label.value)
                }
            }
            val renamed = value.copy(label = label)
            TaxonomyUpdate(replaceValue(key, renamed), renamed)
        }

    fun changeValueMetadata(
        valueId: TagValueId,
        metadata: TagValueMetadata,
    ): Either<ClubTaxonomiaError, TaxonomyUpdate<TagValue>> =
        either {
            val located = findValueWithKey(valueId)
            ensureNotNull(located) { ClubTaxonomiaError.TagValueNotFound }
            val (key, value) = located
            val updated = value.copy(metadata = metadata)
            TaxonomyUpdate(replaceValue(key, updated), updated)
        }

    fun archiveValue(
        valueId: TagValueId,
        at: Instant,
    ): Either<ClubTaxonomiaError, TaxonomyUpdate<TagValue>> =
        either {
            val located = findValueWithKey(valueId)
            ensureNotNull(located) { ClubTaxonomiaError.TagValueNotFound }
            val (key, value) = located
            if (!value.isActive) return@either TaxonomyUpdate(this@Taxonomy, value)
            val archived = value.copy(archivedAt = at)
            TaxonomyUpdate(replaceValue(key, archived), archived)
        }

    /**
     * Reactiva un valor. **Se permite aunque su key esté archivada** —igual que renombrarlo o cambiarle la metadata—
     * porque el archivado no tiene cascada: cada elemento es dueño de su propio `archivedAt`. Mientras la key siga
     * archivada el valor no aparece en [assignableValues]; se ofrecerá solo cuando se reactive también la key. Solo
     * [addValue] bloquea con `Conflict`, porque crear algo nuevo bajo un eje archivado sí carece de sentido.
     */
    fun reactivateValue(valueId: TagValueId): Either<ClubTaxonomiaError, TaxonomyUpdate<TagValue>> =
        either {
            val located = findValueWithKey(valueId)
            ensureNotNull(located) { ClubTaxonomiaError.TagValueNotFound }
            val (key, value) = located
            if (value.isActive) return@either TaxonomyUpdate(this@Taxonomy, value)
            ensure(!hasActiveValueLabel(key, value.label.normalized, excluding = valueId)) {
                ClubTaxonomiaError.DuplicateLabel(TagValue.FIELD, value.label.value)
            }
            val reactivated = value.copy(archivedAt = null)
            TaxonomyUpdate(replaceValue(key, reactivated), reactivated)
        }

    // --- consultas puras ----------------------------------------------------------------------------------------

    /** Keys activas del club (las que forman la taxonomía vigente). */
    fun activeKeys(): List<TagKey> = keys.filter { it.isActive }

    /** Valores ofrecibles para asignar: valores activos de keys activas. */
    fun assignableValues(): List<TagValue> =
        keys.filter { it.isActive }.flatMap { key -> key.values.filter { it.isActive } }

    fun findKey(keyId: TagKeyId): TagKey? = keys.firstOrNull { it.id == keyId }

    fun findValue(valueId: TagValueId): TagValue? =
        keys.firstNotNullOfOrNull { key -> key.values.firstOrNull { it.id == valueId } }

    // --- helpers privados ---------------------------------------------------------------------------------------

    private fun hasActiveKeyLabel(
        normalized: String,
        excluding: TagKeyId?,
    ): Boolean = keys.any { it.isActive && it.id != excluding && it.label.normalized == normalized }

    private fun hasActiveValueLabel(
        key: TagKey,
        normalized: String,
        excluding: TagValueId?,
    ): Boolean = key.values.any { it.isActive && it.id != excluding && it.label.normalized == normalized }

    private fun findValueWithKey(valueId: TagValueId): Pair<TagKey, TagValue>? =
        keys.firstNotNullOfOrNull { key ->
            key.values.firstOrNull { it.id == valueId }?.let { key to it }
        }

    private fun replaceKey(updated: TagKey): Taxonomy =
        copy(keys = keys.map { if (it.id == updated.id) updated else it })

    private fun replaceValue(
        key: TagKey,
        updated: TagValue,
    ): Taxonomy {
        val updatedKey = key.copy(values = key.values.map { if (it.id == updated.id) updated else it })
        return replaceKey(updatedKey)
    }

    companion object {
        /** Taxonomía vacía de un club (bootstrap / antes del seed inicial). */
        fun empty(clubId: ClubId): Taxonomy = Taxonomy(clubId = clubId, keys = emptyList())

        /**
         * Rehidratación desde persistencia: reconstruye el agregado sin revalidar invariantes (los datos ya cumplían
         * las reglas al guardarse; el índice único de BD es la red de seguridad).
         */
        fun rehydrate(
            clubId: ClubId,
            keys: List<TagKey>,
        ): Taxonomy = Taxonomy(clubId = clubId, keys = keys)
    }
}
