package com.runcriticon.clubtaxonomia.infrastructure.persistence.mappers

import arrow.core.getOrElse
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.clubtaxonomia.infrastructure.persistence.entities.TagKeyEntity
import com.runcriticon.clubtaxonomia.infrastructure.persistence.entities.TagValueEntity
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant
import java.util.UUID

/**
 * Traduce entre el agregado [Taxonomy] y sus filas JPA. A mano y no con Konvert: el agregado es un árbol de dos
 * niveles cuya composición (`TagKey.values`) se reconstruye agrupando filas planas por `tag_key_id`, forma que el
 * generador no cubre.
 *
 * La rehidratación **no revalida invariantes** (los datos ya los cumplían al guardarse), pero sí reconstruye
 * [TagLabel], cuyo constructor es privado. Un literal que no pasa esa validación solo puede venir de una fila
 * manipulada fuera de la aplicación: es una precondición imposible y se trata con `error(...)`, no con `Either`.
 */
internal object TaxonomyMapper {
    fun toDomain(
        clubId: ClubId,
        keys: List<TagKeyEntity>,
        values: List<TagValueEntity>,
    ): Taxonomy {
        val valuesByKey = values.groupBy { it.tagKeyId }
        val domainKeys =
            keys.sortedWith(ORDER_KEYS).map { key ->
                TagKey(
                    id = TagKeyId.of(key.id),
                    clubId = clubId,
                    label = keyLabel(key),
                    archivedAt = key.archivedAt,
                    values = valuesByKey[key.id].orEmpty().sortedWith(ORDER_VALUES).map(::toDomain),
                )
            }
        return Taxonomy.rehydrate(clubId, domainKeys)
    }

    fun toEntity(
        key: TagKey,
        clubId: ClubId,
        now: Instant,
    ): TagKeyEntity =
        TagKeyEntity(
            id = key.id.value,
            clubId = clubId.value,
            name = key.label.value,
            archivedAt = key.archivedAt,
            createdAt = now,
        )

    fun toEntity(
        value: TagValue,
        keyId: TagKeyId,
        clubId: ClubId,
        now: Instant,
    ): TagValueEntity =
        TagValueEntity(
            id = value.id.value,
            tagKeyId = keyId.value,
            clubId = clubId.value,
            name = value.label.value,
            metadata = value.metadata,
            archivedAt = value.archivedAt,
            createdAt = now,
        )

    private fun toDomain(value: TagValueEntity): TagValue =
        TagValue(
            id = TagValueId.of(value.id),
            label = valueLabel(value),
            metadata = value.metadata,
            archivedAt = value.archivedAt,
        )

    private fun keyLabel(key: TagKeyEntity): TagLabel =
        TagLabel.forKey(key.name).getOrElse { error(corrupted("tag_key", key.id, it)) }

    private fun valueLabel(value: TagValueEntity): TagLabel =
        TagLabel.forValue(value.name).getOrElse { error(corrupted("tag_value", value.id, it)) }

    private fun corrupted(
        table: String,
        id: UUID,
        reason: Any,
    ): String = "nombre inválido en $table id=$id: $reason (fila manipulada fuera de la aplicación)"

    /**
     * Orden estable de presentación: por antigüedad y, a igualdad de instante (varias altas en la misma transacción),
     * por id — que es UUID v7, ordenado por tiempo de generación.
     */
    private val ORDER_KEYS = compareBy<TagKeyEntity>({ it.createdAt }, { it.id })
    private val ORDER_VALUES = compareBy<TagValueEntity>({ it.createdAt }, { it.id })
}
