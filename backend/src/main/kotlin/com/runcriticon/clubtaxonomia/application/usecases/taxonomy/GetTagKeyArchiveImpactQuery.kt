package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagArchiveImpact
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import java.util.UUID

/**
 * Impacto de archivar un eje (LAL-83): cuántos alumnos tienen asignado alguno de sus valores (informativo) y qué
 * grupos vivos exigen alguno de ellos en su filtro (bloqueante — ver [ArchiveTagKeyCommand]). Solo el ADMIN, misma
 * autorización que archivar, del que es el paso previo.
 */
@ApplicationService
class GetTagKeyArchiveImpactQuery(
    private val taxonomyRepository: TaxonomyRepository,
    private val studentTagRepository: StudentTagRepository,
    private val groupRepository: GroupRepository,
) {
    fun execute(
        actor: Principal,
        keyId: UUID,
    ): Either<ClubTaxonomiaError, TagArchiveImpact> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val id = TagKeyId.of(keyId)
            val key =
                ensureNotNull(taxonomyRepository.findByClub(clubId).findKey(id)) { ClubTaxonomiaError.TagKeyNotFound }
            val valueIds = key.values.mapTo(mutableSetOf()) { it.id }
            TagArchiveImpact(
                studentsAffected = studentTagRepository.countStudentsWithAnyValue(clubId, valueIds),
                groupsRequiring = groupRepository.findGroupsRequiringAnyTagValue(clubId, valueIds),
            )
        }
}
