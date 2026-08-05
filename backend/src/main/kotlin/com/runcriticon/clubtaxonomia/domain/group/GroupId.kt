package com.runcriticon.clubtaxonomia.domain.group

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de un `Group` (grupo del club).
 */
@JvmInline
value class GroupId(
    val value: UUID,
) {
    companion object {
        fun new(): GroupId = GroupId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): GroupId = GroupId(value)
    }
}
