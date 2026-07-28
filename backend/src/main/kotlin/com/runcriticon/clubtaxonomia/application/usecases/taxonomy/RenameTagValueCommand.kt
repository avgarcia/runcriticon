package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Renombra un valor (`TagValue`) de un eje de la taxonomía. Solo el ADMIN.
 */
@ApplicationService
class RenameTagValueCommand(
    private val taxonomyRepository: TaxonomyRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        valueId: UUID,
        rawLabel: String,
    ): Either<ClubTaxonomiaError, TagValue> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                ClubTaxonomiaError.Forbidden
            }
            taxonomyRepository.mutate(actor) { it.renameValue(TagValueId.of(valueId), rawLabel) }.bind()
        }
}
