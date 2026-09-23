package com.runcriticon.planificacion.application.usecases.sessions

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.planificacion.application.PlanificacionAccessAuditor
import com.runcriticon.planificacion.application.ports.outbound.persistence.CoachGroupLookup
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/** Elimina una sesión de un plan. Mismo criterio de autorización que `AddSessionCommand`. */
@ApplicationService
class DeleteSessionCommand(
    private val repository: WeeklyPlanRepository,
    private val coachGroupLookup: CoachGroupLookup,
    private val auditor: PlanificacionAccessAuditor,
) {
    @Transactional
    fun execute(
        actor: Principal,
        planId: PlanId,
        sessionId: SessionId,
    ): Either<PlanificacionError, Unit> =
        either {
            val (clubId, plan) = loadAuthorizedPlan(actor, planId)

            plan.removeSession(sessionId).bind()
            repository.deleteSession(clubId, planId, sessionId)
        }

    /** RBAC → plan cargado → relación vigente con el grupo. Extraído para mantener [execute] dentro del tope de
     * `detekt` — mismo bloque de guardas que `AddSessionCommand`/`UpdateSessionCommand`. */
    private fun Raise<PlanificacionError>.loadAuthorizedPlan(
        actor: Principal,
        planId: PlanId,
    ): Pair<ClubId, WeeklyPlan> {
        ensure(AuthorizationMatrix.can(actor.role, Resource.PLAN, Action.UPDATE)) {
            auditor.denegado(actor, Resource.PLAN, Action.UPDATE, aggregateId = actor.userId, motivo = "RBAC")
            PlanificacionError.Forbidden
        }
        val clubId = ClubId.of(actor.clubId)
        val plan = repository.findById(clubId, planId)
        ensureNotNull(plan) {
            auditor.denegado(actor, Resource.PLAN, Action.UPDATE, aggregateId = planId.value, motivo = "PlanNotFound")
            PlanificacionError.Forbidden
        }
        val coach = PersonId.of(actor.userId)
        ensure(coachGroupLookup.isCoachOfGroup(clubId, coach, plan.groupId)) {
            auditor.denegado(
                actor,
                Resource.PLAN,
                Action.UPDATE,
                aggregateId = planId.value,
                motivo = "NotCoachOfGroup",
                sujetoId = plan.groupId.value,
            )
            PlanificacionError.Forbidden
        }
        return clubId to plan
    }
}
