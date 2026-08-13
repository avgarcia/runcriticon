package com.runcriticon.planificacion.application.ports.outbound.persistence

import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PlanId
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
}
