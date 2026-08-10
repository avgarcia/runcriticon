package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupSummary
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/**
 * Grupos del club con cuánta gente cae dentro de cada uno. El admin y el entrenador.
 *
 * Ambos ven hoy **todos** los grupos del club, no un subconjunto: la relación entrenador-grupo todavía no existe, así
 * que no hay nada por lo que filtrar. Cuando exista, este es el sitio donde acotarlo.
 */
@ApplicationService
class ListGroupsQuery(
    private val groupRepository: GroupRepository,
) {
    @Transactional(readOnly = true)
    fun execute(actor: Principal): Either<ClubTaxonomiaError, List<GroupSummary>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.LIST)) {
                ClubTaxonomiaError.Forbidden
            }
            groupRepository.listSummaries(ClubId.of(actor.clubId))
        }
}
