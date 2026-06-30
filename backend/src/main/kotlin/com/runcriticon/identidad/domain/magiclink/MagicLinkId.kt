package com.runcriticon.identidad.domain.magiclink

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado del magic link (ADR-0008: typed IDs como value class sobre UUID v7).
 * El v7 es ordenable por tiempo, lo que ayuda a la localidad de índices en Postgres.
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
