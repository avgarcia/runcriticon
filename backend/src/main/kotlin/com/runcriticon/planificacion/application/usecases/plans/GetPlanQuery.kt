package com.runcriticon.planificacion.application.usecases.plans

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId

/**
 * El plan semanal completo, con sus sesiones (LAL-24, pantalla de detalle).
 *
 * **Devuelve `Forbidden`, no un resultado vacío**, a diferencia de su hermano `ListDraftPlansQuery`: aquel
 * lista los planes de un grupo, y una lista vacía es una respuesta legítima para "sin relación con el grupo".
 * Aquí se pide **un** plan concreto por id — un detalle no tiene forma "vacía" que devolver sin mentir sobre
 * si existe, así que colapsa "no existe" y "no es tuyo" en `Forbidden`, mismo criterio que el resto del módulo.
 */
@ApplicationService
class GetPlanQuery(
    private val repository: WeeklyPlanRepository,
) {
    fun execute(
        actor: Principal,
        planId: PlanId,
    ): Either<PlanificacionError, WeeklyPlan> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.PLAN, Action.LIST)) {
                PlanificacionError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val plan = repository.findById(clubId, planId)
            ensureNotNull(plan) { PlanificacionError.Forbidden }
            plan
        }
}
