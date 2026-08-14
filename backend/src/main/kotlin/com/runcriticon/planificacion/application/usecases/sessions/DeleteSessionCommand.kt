package com.runcriticon.planificacion.application.usecases.sessions

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.planificacion.application.ports.outbound.persistence.CoachGroupLookup
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/** Elimina una sesión de un plan (LAL-24). Mismo criterio de autorización que `AddSessionCommand`. */
@ApplicationService
class DeleteSessionCommand(
    private val repository: WeeklyPlanRepository,
    private val coachGroupLookup: CoachGroupLookup,
) {
    @Transactional
    fun execute(
        actor: Principal,
        planId: PlanId,
        sessionId: SessionId,
    ): Either<PlanificacionError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.PLAN, Action.UPDATE)) {
                PlanificacionError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val plan = repository.findById(clubId, planId)
            ensureNotNull(plan) { PlanificacionError.Forbidden }
            val coach = PersonId.of(actor.userId)
            ensure(coachGroupLookup.isCoachOfGroup(clubId, coach, plan.groupId)) { PlanificacionError.Forbidden }

            plan.removeSession(sessionId).bind()
            repository.deleteSession(clubId, planId, sessionId)
        }
}
