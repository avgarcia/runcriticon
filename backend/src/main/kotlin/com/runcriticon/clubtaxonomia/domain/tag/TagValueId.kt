package com.runcriticon.clubtaxonomia.domain.tag

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de un `TagValue` (valor permitido de un eje de la taxonomía).
 */
@JvmInline
value class TagValueId(
    val value: UUID,
) {
    companion object {
        fun new(): TagValueId = TagValueId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): TagValueId = TagValueId(value)
    }
}
