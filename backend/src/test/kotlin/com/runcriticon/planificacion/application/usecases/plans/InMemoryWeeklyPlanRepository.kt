package com.runcriticon.planificacion.application.usecases.plans

import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.tenancy.ClubId

/** Doble en memoria de [WeeklyPlanRepository], mismo patrón que `InMemoryGroupRepository` de `clubtaxonomia`. */
class InMemoryWeeklyPlanRepository(
    existing: List<WeeklyPlan> = emptyList(),
) : WeeklyPlanRepository {
    val saved = mutableListOf<Pair<ClubId, WeeklyPlan>>()
    private val plans = existing.toMutableList()

    override fun save(
        clubId: ClubId,
        plan: WeeklyPlan,
    ) {
        saved += clubId to plan
        plans += plan
    }

    override fun findById(
        clubId: ClubId,
        id: PlanId,
    ): WeeklyPlan? = plans.find { it.clubId == clubId && it.id == id }

    override fun listDraftsByGroup(
        clubId: ClubId,
        groupId: GroupId,
    ): List<WeeklyPlan> = plans.filter { it.clubId == clubId && it.groupId == groupId }
}
