package com.runcriticon.planificacion.application.usecases.sessions

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.planificacion.application.ports.outbound.persistence.CoachGroupLookup
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.Pace
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.SessionVolume
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/**
 * Sustituye tipo, volumen, ritmo y notas de una sesión existente (LAL-24).
 *
 * **Sin `dia` entre los parámetros**: el editor no permite mover una sesión de día (decisión 8 del ticket) —
 * el día se toma de la sesión ya cargada, nunca de la petición. Mismo criterio de autorización que
 * [com.runcriticon.planificacion.application.usecases.sessions.AddSessionCommand].
 */
@ApplicationService
class UpdateSessionCommand(
    private val repository: WeeklyPlanRepository,
    private val coachGroupLookup: CoachGroupLookup,
) {
    @Transactional
    fun execute(
        actor: Principal,
        planId: PlanId,
        sessionId: SessionId,
        type: SessionType,
        volume: SessionVolume?,
        pace: Pace?,
        notes: String?,
    ): Either<PlanificacionError, Session> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.PLAN, Action.UPDATE)) {
                PlanificacionError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val plan = repository.findById(clubId, planId)
            ensureNotNull(plan) { PlanificacionError.Forbidden }
            val coach = PersonId.of(actor.userId)
            ensure(coachGroupLookup.isCoachOfGroup(clubId, coach, plan.groupId)) { PlanificacionError.Forbidden }

            val existing = plan.sessions.find { it.id == sessionId }
            ensureNotNull(existing) { PlanificacionError.SessionNotFound }

            val session =
                Session
                    .create(
                        id = sessionId,
                        day = existing.day,
                        type = type,
                        volume = volume,
                        pace = pace,
                        notes = notes,
                    ).bind()
            plan.updateSession(session).bind()
            repository.updateSession(clubId, planId, session)
            session
        }
}
