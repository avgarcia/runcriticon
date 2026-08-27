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

    /**
     * Sustituye la fila `(alumno, plan_id, dia)` de [studentId] por el contenido de [session] (LAL-26): tanto
     * aplicar una personalización (`session` es el override, `isPersonalized = true`) como retirarla
     * (`session` es la sesión base, `isPersonalized = false`) escriben por aquí — mismo criterio que
     * `ConsentProjection.upsert(granted: Boolean, ...)`, un único escritor, el llamador decide el contenido.
     *
     * **`UPDATE`-only, no upsert**: `sesion_resuelta` es `NOT NULL`, no se puede insertar una fila que no
     * exista. La fila siempre existe cuando el evento es real (solo se emite con el plan ya `PUBLICADO`); si
     * no existe todavía (outbox entregó `PersonalizacionAplicada` antes que `PlanPublicado`) o la guarda de
     * orden descarta el escrito, el `UPDATE` no toca filas — no-op silencioso y correcto, no un error.
     *
     * **Guarda de orden por [occurredAt]**: aplicar y retirar alternan sobre la misma fila, y el outbox no
     * garantiza orden de entrega entre eventos de agregados distintos — mismo criterio que
     * `ConsentProjectionJdbc.UPSERT_SQL`.
     *
     * @return `false` si no tocó ninguna fila (no existía, o la guarda de orden la descartó).
     */
    fun writePersonalizedSession(
        clubId: ClubId,
        studentId: StudentId,
        session: ResolvedSession,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean
}
