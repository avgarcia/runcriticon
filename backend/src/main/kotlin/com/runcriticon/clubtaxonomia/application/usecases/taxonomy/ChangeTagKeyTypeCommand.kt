package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyType
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Cambia el tipo de un eje (`TagKey`). Solo el ADMIN. Idempotente si ya tiene ese tipo. Degradar de
 * [TagKeyType.RACE] a [TagKeyType.SIMPLE] se rechaza con [ClubTaxonomiaError.Conflict]
 * (`"tag_key_has_race_values"`) si algún valor del eje conserva metadata de carrera —
 * `Taxonomy.changeKeyType` es quien aplica esta regla.
 */
@ApplicationService
class ChangeTagKeyTypeCommand(
    private val taxonomyRepository: TaxonomyRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    @Transactional
    fun execute(
        actor: Principal,
        keyId: UUID,
        type: TagKeyType,
    ): Either<ClubTaxonomiaError, TagKey> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                auditor.denegado(actor, Resource.TAXONOMY, Action.MANAGE)
                ClubTaxonomiaError.Forbidden
            }
            taxonomyRepository.mutate(actor) { it.changeKeyType(TagKeyId.of(keyId), type) }.bind()
        }
}
