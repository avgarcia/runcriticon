package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Archiva un valor (`TagValue`) de un eje de la taxonomía (soft-delete). Solo el ADMIN. Idempotente.
 *
 * Se rechaza con [ClubTaxonomiaError.TagValueRequiredByGroup] si es requerido por el filtro de un grupo vivo
 * (ADR-0002 D10, "reglas de bloqueo"): el admin debe reescribir el filtro de esos grupos primero.
 * `GetTagValueArchiveImpactQuery` da esa lista antes de intentarlo.
 */
@ApplicationService
class ArchiveTagValueCommand(
    private val taxonomyRepository: TaxonomyRepository,
    private val groupRepository: GroupRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    @Transactional
    fun execute(
        actor: Principal,
        valueId: UUID,
    ): Either<ClubTaxonomiaError, TagValue> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                auditor.denegado(actor, Resource.TAXONOMY, Action.MANAGE)
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val id = TagValueId.of(valueId)
            ensure(taxonomyRepository.findByClub(clubId).findValue(id) != null) { ClubTaxonomiaError.TagValueNotFound }
            val blockingGroups = groupRepository.findGroupIdsByAnyRequiredTagValue(clubId, setOf(id))
            ensure(blockingGroups.isEmpty()) { ClubTaxonomiaError.TagValueRequiredByGroup(blockingGroups) }
            taxonomyRepository.mutate(actor) { it.archiveValue(id, Instant.now()) }.bind()
        }
}
