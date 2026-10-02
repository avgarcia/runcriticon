package com.runcriticon.planificacion.application.usecases.plans

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.planificacion.application.PlanificacionAccessAuditor
import com.runcriticon.planificacion.application.ports.outbound.persistence.CoachGroupLookup
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/**
 * El plan semanal completo, con sus sesiones (pantalla de detalle).
 *
 * **Devuelve `Forbidden`, no un resultado vacío**, a diferencia de su hermano `ListDraftPlansQuery`: aquel
 * lista los planes de un grupo, y una lista vacía es una respuesta legítima para "sin relación con el grupo".
 * Aquí se pide **un** plan concreto por id — un detalle no tiene forma "vacía" que devolver sin mentir sobre
 * si existe, así que colapsa "no existe", "de otro club" y "no es tuyo" en `Forbidden`, mismo criterio que el
 * resto del módulo. **Orden de guardas**, igual que `PublishPlanCommand`: RBAC → carga del plan (ya filtrada
 * por `club_id`) → relación entrenador↔grupo (`CoachGroupLookup`).
 */
@ApplicationService
class GetPlanQuery(
    private val repository: WeeklyPlanRepository,
    private val coachGroupLookup: CoachGroupLookup,
    private val auditor: PlanificacionAccessAuditor,
) {
    @Transactional
    fun execute(
        actor: Principal,
        planId: PlanId,
    ): Either<PlanificacionError, WeeklyPlan> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.PLAN, Action.LIST)) {
                auditor.denegado(actor, Resource.PLAN, Action.LIST, aggregateId = actor.userId, motivo = "RBAC")
                PlanificacionError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val plan = repository.findById(clubId, planId)
            ensureNotNull(plan) {
                auditor.denegado(actor, Resource.PLAN, Action.LIST, aggregateId = planId.value, motivo = "PlanNotFound")
                PlanificacionError.Forbidden
            }
            val coach = PersonId.of(actor.userId)
            ensure(coachGroupLookup.isCoachOfGroup(clubId, coach, plan.groupId)) {
                auditor.denegado(
                    actor,
                    Resource.PLAN,
                    Action.LIST,
                    aggregateId = planId.value,
                    motivo = "NotCoachOfGroup",
                    sujetoId = plan.groupId.value,
                )
                PlanificacionError.Forbidden
            }
            plan
        }
}
