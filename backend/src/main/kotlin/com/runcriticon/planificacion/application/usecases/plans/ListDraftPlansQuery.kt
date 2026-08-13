package com.runcriticon.planificacion.application.usecases.plans

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.planificacion.application.ports.outbound.persistence.CoachGroupLookup
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import java.util.UUID

/**
 * Los planes en borrador de un grupo (AC7, "mis planes en borrador por grupo").
 *
 * **Sin `CoachNotFound`/`Forbidden` cuando el entrenador no tiene relación con el grupo**: a diferencia de
 * `CreateDraftPlanCommand`, una lectura sin relación devuelve lista vacía en vez de 403 — mismo criterio que
 * `resolveMembers` de `clubtaxonomia` (`groupId` de otro club también da vacío, no error). Es más simple para el
 * cliente y no revela nada que un 403 no revelara ya.
 */
@ApplicationService
class ListDraftPlansQuery(
    private val repository: WeeklyPlanRepository,
    private val coachGroupLookup: CoachGroupLookup,
) {
    fun execute(
        actor: Principal,
        groupId: UUID,
    ): Either<PlanificacionError, List<WeeklyPlan>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.PLAN, Action.LIST)) {
                PlanificacionError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val group = GroupId.of(groupId)
            val coach = PersonId.of(actor.userId)

            if (coachGroupLookup.isCoachOfGroup(clubId, coach, group)) {
                repository.listDraftsByGroup(clubId, group)
            } else {
                emptyList()
            }
        }
}
