package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/**
 * Devuelve la taxonomía completa del club. La consultan ADMIN y ENTRENADOR; el ALUMNO queda fuera (por eso pasa por
 * la matriz y no por `@AuthenticatedOnly`).
 */
@ApplicationService
class ListTaxonomyQuery(
    private val taxonomyRepository: TaxonomyRepository,
) {
    @Transactional(readOnly = true)
    fun execute(actor: Principal): Either<ClubTaxonomiaError, Taxonomy> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.LIST)) {
                ClubTaxonomiaError.Forbidden
            }
            taxonomyRepository.findByClub(ClubId.of(actor.clubId))
        }
}
