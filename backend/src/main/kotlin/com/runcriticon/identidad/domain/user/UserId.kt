package com.runcriticon.identidad.domain.user

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado del usuario.
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
