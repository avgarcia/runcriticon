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
 * Retira la marca de archivado de un eje (`TagKey`). Solo el ADMIN. Idempotente sobre un eje ya activo.
 *
 * Puede fallar con `DuplicateLabel`: mientras el eje estaba archivado su nombre quedó libre, así que otro eje activo
 * puede habérselo quedado. No reactiva sus valores — el archivado no tiene cascada en ninguno de los dos sentidos.
 */
@ApplicationService
class ReactivateTagKeyCommand(
    private val taxonomyRepository: TaxonomyRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        keyId: UUID,
    ): Either<ClubTaxonomiaError, TagKey> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                ClubTaxonomiaError.Forbidden
            }
            taxonomyRepository.mutate(actor) { it.reactivateKey(TagKeyId.of(keyId)) }.bind()
        }
}
