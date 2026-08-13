package com.runcriticon.planificacion.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de un `WeeklyPlan`.
 */
@JvmInline
value class PlanId(
    val value: UUID,
) {
    companion object {
        fun new(): PlanId = PlanId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): PlanId = PlanId(value)
    }
}
