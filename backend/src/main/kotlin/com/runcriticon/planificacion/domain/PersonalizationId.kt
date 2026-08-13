package com.runcriticon.planificacion.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de una `Personalization` (entidad hija de `WeeklyPlan`, sin caso de uso todavía — LAL-26).
 */
@JvmInline
value class PersonalizationId(
    val value: UUID,
) {
    companion object {
        fun new(): PersonalizationId = PersonalizationId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): PersonalizationId = PersonalizationId(value)
    }
}
