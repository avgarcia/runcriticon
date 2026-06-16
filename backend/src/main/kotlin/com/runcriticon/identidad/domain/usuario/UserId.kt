package com.runcriticon.identidad.domain.usuario

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado del usuario (ADR-0008: typed IDs como value class sobre UUID v7).
 * El v7 es ordenable por tiempo, lo que ayuda a la localidad de índices en Postgres.
 */
@JvmInline
value class UserId(
    val value: UUID,
) {
    companion object {
        fun new(): UserId = UserId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): UserId = UserId(value)
    }
}
