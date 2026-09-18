package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
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
 * Archiva un eje (`TagKey`) de la taxonomía del club (soft-delete). Solo el ADMIN. Idempotente: re-archivar conserva
 * la marca original.
 *
 * Se rechaza con [ClubTaxonomiaError.TagKeyRequiredByGroup] si alguno de sus valores es requerido por el filtro de
 * un grupo vivo (ADR-0002 D10, "reglas de bloqueo"): el admin debe reescribir el filtro de esos grupos primero.
 * `GetTagKeyArchiveImpactQuery` da esa lista antes de intentarlo.
 */
@ApplicationService
class ArchiveTagKeyCommand(
    private val taxonomyRepository: TaxonomyRepository,
    private val groupRepository: GroupRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    @Transactional
    fun execute(
        actor: Principal,
        keyId: UUID,
    ): Either<ClubTaxonomiaError, TagKey> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.TAXONOMY, Action.MANAGE)) {
                auditor.denegado(actor, Resource.TAXONOMY, Action.MANAGE)
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val id = TagKeyId.of(keyId)
            val key =
                ensureNotNull(taxonomyRepository.findByClub(clubId).findKey(id)) { ClubTaxonomiaError.TagKeyNotFound }
            val valueIds = key.values.mapTo(mutableSetOf()) { it.id }
            val blockingGroups = groupRepository.findGroupIdsByAnyRequiredTagValue(clubId, valueIds)
            ensure(blockingGroups.isEmpty()) { ClubTaxonomiaError.TagKeyRequiredByGroup(blockingGroups) }
            taxonomyRepository.mutate(actor) { it.archiveKey(id, Instant.now()) }.bind()
        }
}
