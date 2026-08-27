package com.runcriticon.planificacion.application.usecases.plans

import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.Personalization
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanStatus
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.tenancy.ClubId

/**
 * Doble en memoria de [WeeklyPlanRepository], mismo patrón que `InMemoryGroupRepository` de `clubtaxonomia`.
 *
 * `insertSession`/`updateSession`/`deleteSession` **no repiten la validación de `UNIQUE (plan_id, dia)`**: quien
 * la rechaza es `WeeklyPlan.addSession` (dominio), ya invocado por el caso de uso antes de llegar aquí. Repetirla
 * en el doble escondería en qué capa muerde de verdad la regla — la constraint SQL real la cubre el test de
 * Testcontainers de `WeeklyPlanRepositoryJdbc`.
 */
class InMemoryWeeklyPlanRepository(
    existing: List<WeeklyPlan> = emptyList(),
) : WeeklyPlanRepository {
    val saved = mutableListOf<Pair<ClubId, WeeklyPlan>>()
    val published = mutableListOf<Triple<ClubId, PlanId, Set<PersonId>>>()
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

    override fun insertSession(
        clubId: ClubId,
        planId: PlanId,
        session: Session,
    ) {
        withScopedPlan(clubId, planId) { it.copy(sessions = it.sessions + session) }
    }

    override fun updateSession(
        clubId: ClubId,
        planId: PlanId,
        session: Session,
    ) {
        withScopedPlan(clubId, planId) { plan ->
            plan.copy(sessions = plan.sessions.map { if (it.id == session.id) session else it })
        }
    }

    override fun deleteSession(
        clubId: ClubId,
        planId: PlanId,
        sessionId: SessionId,
    ) {
        withScopedPlan(clubId, planId) { plan -> plan.copy(sessions = plan.sessions.filterNot { it.id == sessionId }) }
    }

    override fun publish(
        clubId: ClubId,
        planId: PlanId,
        snapshot: Set<PersonId>,
    ) {
        published += Triple(clubId, planId, snapshot)
        withScopedPlan(clubId, planId) { it.copy(status = PlanStatus.PUBLICADO) }
    }

    /** Upsert por `(sessionId, studentId)` — mismo criterio que el `ON CONFLICT` real (LAL-26). */
    override fun upsertPersonalization(
        clubId: ClubId,
        planId: PlanId,
        personalization: Personalization,
    ) {
        withScopedPlan(clubId, planId) { plan ->
            val without =
                plan.personalizations.filterNot {
                    it.sessionId == personalization.sessionId && it.studentId == personalization.studentId
                }
            plan.copy(personalizations = without + personalization)
        }
    }

    override fun deletePersonalization(
        clubId: ClubId,
        planId: PlanId,
        sessionId: SessionId,
        studentId: PersonId,
    ) {
        withScopedPlan(clubId, planId) { plan ->
            plan.copy(
                personalizations =
                    plan.personalizations.filterNot { it.sessionId == sessionId && it.studentId == studentId },
            )
        }
    }

    /** Mira el snapshot congelado por la última llamada a [publish] para `(clubId, planId)`. */
    override fun isStudentInSnapshot(
        clubId: ClubId,
        planId: PlanId,
        studentId: PersonId,
    ): Boolean = published.any { (c, p, snapshot) -> c == clubId && p == planId && studentId in snapshot }

    /** Mismo filtro anti-IDOR que la query real: sin efecto si `planId` no pertenece a `clubId`. */
    private fun withScopedPlan(
        clubId: ClubId,
        planId: PlanId,
        mutate: (WeeklyPlan) -> WeeklyPlan,
    ) {
        val index = plans.indexOfFirst { it.clubId == clubId && it.id == planId }
        if (index == -1) return
        plans[index] = mutate(plans[index])
    }
}
