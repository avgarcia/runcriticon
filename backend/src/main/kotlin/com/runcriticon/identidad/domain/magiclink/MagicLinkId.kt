package com.runcriticon.identidad.domain.magiclink

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado del magic link.
 */
@JvmInline
value class MagicLinkId(
    val value: UUID,
) {
    companion object {
        fun new(): MagicLinkId = MagicLinkId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): MagicLinkId = MagicLinkId(value)
    }
}
