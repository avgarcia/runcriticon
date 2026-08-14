package com.runcriticon.planificacion.application.ports.outbound.persistence

import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.tenancy.ClubId

/**
 * Persistencia de `WeeklyPlan` con sus entidades hijas (`Session`, `Personalization`) cargadas eager
 * (ADR-0008 D17).
 */
interface WeeklyPlanRepository {
    /** Persiste [plan]. Solo alta: `CreateDraftPlanCommand` es el único escritor hoy. */
    fun save(
        clubId: ClubId,
        plan: WeeklyPlan,
    )

    /** El plan con id [id], o `null` si no existe o no pertenece a [clubId]. */
    fun findById(
        clubId: ClubId,
        id: PlanId,
    ): WeeklyPlan?

    /** Los planes en borrador de [groupId], ordenados por semana. Lista vacía si el grupo no tiene ninguno. */
    fun listDraftsByGroup(
        clubId: ClubId,
        groupId: GroupId,
    ): List<WeeklyPlan>

    /**
     * Inserta [session] en el plan [planId]. Filtro anti-IDOR en la query (`WHERE p.id = ? AND p.club_id = ?`,
     * mismo patrón que `GroupRepositoryJdbc.assignCoach`): un `planId` que no pertenece a [clubId] no inserta
     * nada. El caso de uso ya cargó el plan con `findById` antes de llamar aquí — esta es la segunda capa.
     */
    fun insertSession(
        clubId: ClubId,
        planId: PlanId,
        session: Session,
    )

    /** Sustituye la sesión con el mismo id que [session] dentro del plan [planId]. */
    fun updateSession(
        clubId: ClubId,
        planId: PlanId,
        session: Session,
    )

    /** Elimina la sesión [sessionId] del plan [planId]. */
    fun deleteSession(
        clubId: ClubId,
        planId: PlanId,
        sessionId: SessionId,
    )
}
