package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupCoach
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Entrenadores asignados a un grupo. El admin y el entrenador, con `Action.LIST` y no `ASSIGN_COACH`: leer quién
 * lleva un grupo no es la mutación privilegiada, mismo precedente que ya fija [GetGroupDetailQuery] frente a
 * [OverrideGroupMembershipCommand].
 *
 * Un grupo que no existe y uno de otro club dan el mismo `GroupNotFound`, mismo criterio que [GetGroupDetailQuery].
 */
@ApplicationService
class ListGroupCoachesQuery(
    private val groupRepository: GroupRepository,
) {
    @Transactional(readOnly = true)
    fun execute(
        actor: Principal,
        groupId: UUID,
    ): Either<ClubTaxonomiaError, List<GroupCoach>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.LIST)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val group = GroupId.of(groupId)

            ensureGroupOfClub(groupRepository, clubId, group)

            groupRepository.findCoaches(clubId, group)
        }
}
