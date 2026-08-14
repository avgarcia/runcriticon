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
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.SessionVolume
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Añade una sesión a un plan existente (LAL-24, editor de sesión).
 *
 * **Autorización: matriz → plan cargado → relación vigente con el grupo.** No `plan.coachId == actor.userId`:
 * comparar solo con el dueño original dejaría editar a un entrenador ya expulsado del grupo. Se revalida
 * `CoachGroupLookup.isCoachOfGroup` en cada mutación, mismo criterio que `CreateDraftPlanCommand` al crear el
 * plan — haber sido su creador en el pasado no basta.
 */
@ApplicationService
class AddSessionCommand(
    private val repository: WeeklyPlanRepository,
    private val coachGroupLookup: CoachGroupLookup,
) {
    @Transactional
    fun execute(
        actor: Principal,
        planId: PlanId,
        day: LocalDate,
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

            val session = Session.create(day = day, type = type, volume = volume, pace = pace, notes = notes).bind()
            // Se descarta el `WeeklyPlan` devuelto: solo sirve para validar los invariantes relativos al plan
            // (día en semana, día libre) antes de escribir. La persistencia real es `insertSession`, acotada
            // por `plan_id`/`club_id` — no hay `save()` idempotente que reescriba el agregado entero.
            plan.addSession(session).bind()
            repository.insertSession(clubId, planId, session)
            session
        }
}
