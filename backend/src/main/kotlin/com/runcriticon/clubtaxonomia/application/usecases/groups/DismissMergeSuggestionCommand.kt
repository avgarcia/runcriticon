package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.MergeSuggestionMetrics
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.MergeSuggestionRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestion
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Descarta una sugerencia de fusión (LAL-96, AC3): no vuelve a aparecer mientras la condición que la generó siga
 * vigente -- [MergeSuggestionListener] respeta el descarte al recalcular (ver el KDoc de
 * [MergeSuggestionRepository.upsert]). El admin y el entrenador.
 *
 * Recibe [groupIdX]/[groupIdY] en cualquier orden y los canonicaliza con [MergeSuggestion.canonicalPair] antes
 * de tocar el repositorio -- el cliente no tiene por qué conocer cuál de los dos es "A" y cuál es "B", esa es una
 * decisión interna de almacenamiento. En [MergeSuggestionType.MICRO] da igual el orden: los dos ids son el mismo.
 */
@ApplicationService
class DismissMergeSuggestionCommand(
    private val suggestionRepository: MergeSuggestionRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
    private val metrics: MergeSuggestionMetrics,
) {
    @Transactional
    fun execute(
        actor: Principal,
        groupIdX: UUID,
        groupIdY: UUID,
        type: MergeSuggestionType,
    ): Either<ClubTaxonomiaError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP_MERGE_SUGGESTION, Action.DISMISS)) {
                auditor.denegado(actor, Resource.GROUP_MERGE_SUGGESTION, Action.DISMISS)
                ClubTaxonomiaError.Forbidden
            }
            val (a, b) = MergeSuggestion.canonicalPair(GroupId.of(groupIdX), GroupId.of(groupIdY))
            val dismissed = suggestionRepository.dismiss(ClubId.of(actor.clubId), a, b, type)
            ensure(dismissed) { ClubTaxonomiaError.MergeSuggestionNotFound }
            metrics.dismissed(type)
        }
}
