package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Renombra un eje (`TagKey`) de la taxonomía del club. Solo el ADMIN.
 */
@ApplicationService
class RenameTagKeyCommand(
    private val taxonomyRepository: TaxonomyRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        keyId: UUID,
        rawLabel: String,
    ): Either<ClubTaxonomiaError, TagKey> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                ClubTaxonomiaError.Forbidden
            }
            taxonomyRepository.mutate(actor) { it.renameKey(TagKeyId.of(keyId), rawLabel) }.bind()
        }
}
