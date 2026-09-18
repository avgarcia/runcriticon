package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Añade un valor (`TagValue`) a un eje de la taxonomía. Solo el ADMIN. [metadata] ausente (`null`) crea el valor
 * sin metadata; con ella, el eje debe ser de tipo [com.runcriticon.clubtaxonomia.domain.tag.TagKeyType.RACE]
 * (`Taxonomy.addValue` lo rechaza si no).
 */
@ApplicationService
class AddTagValueCommand(
    private val taxonomyRepository: TaxonomyRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    @Transactional
    fun execute(
        actor: Principal,
        keyId: UUID,
        rawLabel: String,
        metadata: RaceMetadataInput? = null,
    ): Either<ClubTaxonomiaError, TagValue> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                auditor.denegado(actor, Resource.TAXONOMY, Action.MANAGE)
                ClubTaxonomiaError.Forbidden
            }
            val resolvedMetadata = toMetadata(metadata)
            taxonomyRepository.mutate(actor) { it.addValue(TagKeyId.of(keyId), rawLabel, resolvedMetadata) }.bind()
        }
}
