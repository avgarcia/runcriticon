package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.tenancy.ClubId

/**
 * Lectura de marcas para resolver ritmos relativos desde un listener del outbox (LAL-32), sin principal.
 * Puerto aparte de [StudentMarkRepository] por el mismo criterio que separa `ResolvedPlanProjection` de
 * `ResolvedPlanReader`: aquel sirve `@ApplicationService` dentro de una petición con `Principal` y va
 * `@AuthScope(Scope.CLUB)` — `AuthScopeEnforcementAspect` falla cerrado sin principal (ver su KDoc) — este
 * corre en un `@ApplicationModuleListener`, sin `SecurityContext`, con el `club_id` del propio evento.
 */
interface StudentMarkLookup {
    /** La marca de [studentId] en [distance], o `null` si no la tiene todavía. */
    fun findMark(
        clubId: ClubId,
        studentId: StudentId,
        distance: RaceDistance,
    ): StudentMark?

    /**
     * Las marcas de todos los [students] en una sola consulta — evita el N+1 al proyectar un `PlanPublicado`
     * con varios alumnos en el snapshot. Alumnos sin ninguna marca no aparecen como clave.
     */
    fun findMarks(
        clubId: ClubId,
        students: Set<StudentId>,
    ): Map<StudentId, Map<RaceDistance, StudentMark>>
}
