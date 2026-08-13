package com.runcriticon.planificacion.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de una `Session` (entidad hija de `WeeklyPlan`).
 */
@JvmInline
value class SessionId(
    val value: UUID,
) {
    companion object {
        fun new(): SessionId = SessionId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): SessionId = SessionId(value)
    }
}
