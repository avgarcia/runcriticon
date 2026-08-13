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
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

/**
 * Crea un plan semanal en borrador para un grupo. El entrenador se crea el plan a sí mismo — solo `ENTRENADOR` en
 * la matriz (ver comentario en `AuthorizationMatrix`), así que [actor] es siempre el entrenador dueño del plan.
 *
 * **El orden de las guardas no es casual**, mismo criterio que `AssignCoachToGroupCommand` de `clubtaxonomia`:
 * primero RBAC (¿este rol puede crear planes en absoluto?), luego la relación con el objeto concreto
 * ([CoachGroupLookup.isCoachOfGroup]) — que colapsa "grupo inexistente" y "grupo ajeno" en el mismo
 * `PlanificacionError.Forbidden`, porque este módulo no tiene forma de confirmar por sí solo que el grupo exista
 * más allá de su propia proyección local.
 */
@ApplicationService
class CreateDraftPlanCommand(
    private val repository: WeeklyPlanRepository,
    private val coachGroupLookup: CoachGroupLookup,
) {
    @Transactional
    fun execute(
        actor: Principal,
        groupId: UUID,
        week: LocalDate,
    ): Either<PlanificacionError, WeeklyPlan> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.PLAN, Action.CREATE)) {
                PlanificacionError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val group = GroupId.of(groupId)
            val coach = PersonId.of(actor.userId)

            ensure(coachGroupLookup.isCoachOfGroup(clubId, coach, group)) { PlanificacionError.Forbidden }

            val plan = WeeklyPlan.createDraft(clubId, group, coach, week).bind()
            repository.save(clubId, plan)
            plan
        }
}
