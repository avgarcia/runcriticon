package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
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
 * Reemplaza por completo la metadata de un valor (`TagValue`) ya existente. Solo el ADMIN. [metadata] `null` la
 * vacía; con ella, el eje del valor debe ser de tipo
 * [com.runcriticon.clubtaxonomia.domain.tag.TagKeyType.RACE] (`Taxonomy.changeValueMetadata` lo rechaza si no).
 */
@ApplicationService
class ChangeTagValueMetadataCommand(
    private val taxonomyRepository: TaxonomyRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    @Transactional
    fun execute(
        actor: Principal,
        valueId: UUID,
        metadata: RaceMetadataInput?,
    ): Either<ClubTaxonomiaError, TagValue> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                auditor.denegado(actor, Resource.TAXONOMY, Action.MANAGE)
                ClubTaxonomiaError.Forbidden
            }
            val resolvedMetadata = toMetadata(metadata)
            taxonomyRepository.mutate(actor) { it.changeValueMetadata(TagValueId.of(valueId), resolvedMetadata) }.bind()
        }
}
