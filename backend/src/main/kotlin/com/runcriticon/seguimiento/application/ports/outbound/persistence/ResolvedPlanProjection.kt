package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant
import java.util.UUID

/**
 * Escritura de la proyección `plan_resuelto_por_alumno`, alimentada por `PlanPublicado`
 * (`ResolvedPlanProjectionListener`). Puerto aparte de [ResolvedPlanReader], mismo criterio que
 * `PersonProjection`/`StudentDirectory` en `clubtaxonomia`: este corre en el listener del outbox sin
 * principal, aquel corre dentro de una petición con `Principal` y se somete al filtro de club.
 */
interface ResolvedPlanProjection {
    /**
     * Materializa, para cada alumno de [students], una fila por cada sesión de [sessions] del plan [planId].
     *
     * Upsert por `(alumno_id, plan_id, dia)`: un plan publicado no vuelve a mutar (`WeeklyPlan.publish` es
     * terminal), así que no hace falta guarda de orden por fila — la única razón para reescribir la misma
     * clave es un reintento del outbox tras un fallo a mitad de la transacción anterior, y ahí el mismo valor
     * gana siempre.
     */
    fun replacePlan(
        clubId: ClubId,
        planId: PlanId,
        students: Set<StudentId>,
        sessions: List<ResolvedSession>,
        eventId: UUID,
        occurredAt: Instant,
    )

    /**
     * Retraso en segundos: `now()` menos el `occurredAt` del `PlanPublicado` más reciente ya aplicado. `0` si la
     * proyección está vacía. Alimenta el gauge `seguimiento.projection_lag_seconds` (métrica obligatoria por
     * módulo; `planificacion` no la tiene — no repetir esa omisión).
     */
    fun lagSeconds(): Long
}
