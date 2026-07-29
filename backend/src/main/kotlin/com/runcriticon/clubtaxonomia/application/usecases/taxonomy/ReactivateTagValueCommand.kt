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
 * Retira la marca de archivado de un valor (`TagValue`). Solo el ADMIN. Idempotente sobre un valor ya activo.
 *
 * Se permite aunque su eje siga archivado: cada elemento es dueño de su propio archivado. Mientras el eje lo esté, el
 * valor no aparecerá entre los asignables. Puede fallar con `DuplicateLabel` si su literal fue reocupado por otro
 * valor activo del mismo eje.
 */
@ApplicationService
class ReactivateTagValueCommand(
    private val taxonomyRepository: TaxonomyRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        valueId: UUID,
    ): Either<ClubTaxonomiaError, TagValue> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                ClubTaxonomiaError.Forbidden
            }
            taxonomyRepository.mutate(actor) { it.reactivateValue(TagValueId.of(valueId)) }.bind()
        }
}
