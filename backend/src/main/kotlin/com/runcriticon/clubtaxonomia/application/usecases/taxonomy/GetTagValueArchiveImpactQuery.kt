package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagArchiveImpact
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import java.util.UUID

/**
 * Impacto de archivar un valor (LAL-83): cuántos alumnos lo tienen asignado (informativo) y qué grupos vivos lo
 * exigen en su filtro (bloqueante — ver [ArchiveTagValueCommand]). Solo el ADMIN, misma autorización que archivar,
 * del que es el paso previo.
 */
@ApplicationService
class GetTagValueArchiveImpactQuery(
    private val taxonomyRepository: TaxonomyRepository,
    private val studentTagRepository: StudentTagRepository,
    private val groupRepository: GroupRepository,
) {
    fun execute(
        actor: Principal,
        valueId: UUID,
    ): Either<ClubTaxonomiaError, TagArchiveImpact> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val id = TagValueId.of(valueId)
            ensure(taxonomyRepository.findByClub(clubId).findValue(id) != null) { ClubTaxonomiaError.TagValueNotFound }
            val valueIds = setOf(id)
            TagArchiveImpact(
                studentsAffected = studentTagRepository.countStudentsWithAnyValue(clubId, valueIds),
                groupsRequiring = groupRepository.findGroupsRequiringAnyTagValue(clubId, valueIds),
            )
        }
}
