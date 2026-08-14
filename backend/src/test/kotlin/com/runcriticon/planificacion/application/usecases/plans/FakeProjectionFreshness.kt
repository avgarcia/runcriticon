package com.runcriticon.planificacion.application.usecases.plans

import com.runcriticon.planificacion.application.ports.outbound.ProjectionFreshness

/** Doble de [ProjectionFreshness] con un lag fijo, para probar la puerta fail-closed sin Postgres real. */
class FakeProjectionFreshness(
    private val lagSeconds: Long = 0L,
) : ProjectionFreshness {
    override fun membersProjectionLagSeconds(): Long = lagSeconds
}
