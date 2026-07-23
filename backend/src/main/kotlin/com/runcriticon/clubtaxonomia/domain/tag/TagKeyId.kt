package com.runcriticon.clubtaxonomia.domain.tag

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de un `TagKey` (eje de la taxonomía).
 */
@JvmInline
value class TagKeyId(
    val value: UUID,
) {
    companion object {
        fun new(): TagKeyId = TagKeyId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): TagKeyId = TagKeyId(value)
    }
}
