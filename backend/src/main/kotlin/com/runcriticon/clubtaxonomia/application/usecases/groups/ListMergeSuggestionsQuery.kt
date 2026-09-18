package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.MergeSuggestionRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionOverview
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/**
 * Sugerencias de fusión activas del club (LAL-96): las mantiene al día [MergeSuggestionListener], esta consulta
 * solo lee lo ya calculado -- ninguna resolución de membresía en la ruta de lectura (AC4). El admin y el
 * entrenador.
 */
@ApplicationService
class ListMergeSuggestionsQuery(
    private val suggestionRepository: MergeSuggestionRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    @Transactional
    fun execute(actor: Principal): Either<ClubTaxonomiaError, List<MergeSuggestionOverview>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP_MERGE_SUGGESTION, Action.LIST)) {
                auditor.denegado(actor, Resource.GROUP_MERGE_SUGGESTION, Action.LIST)
                ClubTaxonomiaError.Forbidden
            }
            suggestionRepository.listActive(ClubId.of(actor.clubId))
        }
}
