package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Desvincula a un entrenador de un grupo. **Solo el ADMIN**, mismo motivo que [AssignCoachToGroupCommand].
 *
 * Idempotente y **no comprueba que el entrenador siga existiendo** — mismo criterio que
 * [ClearGroupMembershipOverrideCommand]: una asignación de quien ya no existe es justo la que hay que poder
 * limpiar, y exigirlo bloquearía esa limpieza.
 */
@ApplicationService
class UnassignCoachFromGroupCommand(
    private val groupRepository: GroupRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        groupId: UUID,
        coachId: UUID,
    ): Either<ClubTaxonomiaError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.ASSIGN_COACH)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val group = GroupId.of(groupId)

            ensureGroupOfClub(groupRepository, clubId, group)

            // El número de filas borradas se descarta: la operación es idempotente de cara a quien llama, que no
            // tiene por qué saber si existía la asignación.
            groupRepository.unassignCoach(clubId, group, PersonId.of(coachId))
            Unit
        }
}
