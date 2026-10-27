package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.application.usecases.HEALTH_TAG_RESOURCE
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.taxonomy.healthDataValueIds
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.rgpd.AccessType
import com.runcriticon.shared.rgpd.AuditAccess
import com.runcriticon.shared.rgpd.AuditSubjects
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Alumnos que cumplirían un filtro de tags que todavía no se ha guardado: alimenta el contador y la lista del
 * constructor de grupos mientras se añaden y quitan condiciones. El admin y el entrenador.
 *
 * No persiste nada. Valida el filtro con el mismo criterio que el alta para no ofrecer una previsualización de algo
 * que luego el alta rechazaría.
 *
 * Un filtro vacío devuelve cero alumnos, no el club entero: es la misma semántica que la de un grupo guardado sin
 * tags requeridos.
 *
 * `@AuditAccess` (ADR-0009 D15): un filtro que exige un valor de salud (`lesión`, `post-parto`) devuelve la lista
 * nominal de quienes lo llevan en el club, así que entonces se audita a todos los alumnos devueltos.
 */
@ApplicationService
class PreviewGroupMembersQuery(
    private val taxonomyRepository: TaxonomyRepository,
    private val groupRepository: GroupRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    /** Los alumnos que cumplen el filtro; [auditSubjectIds] son todos ellos si el filtro exige un valor de salud. */
    data class Result(
        val members: GroupMembers,
        private val filterRequiresHealthData: Boolean,
    ) : AuditSubjects {
        override fun auditSubjectIds(): Set<UUID> =
            if (filterRequiresHealthData) members.members.mapTo(mutableSetOf()) { it.id.value } else emptySet()
    }

    @AuditAccess(type = AccessType.SALUD, resource = HEALTH_TAG_RESOURCE)
    @Transactional
    fun execute(
        actor: Principal,
        tagValueIds: List<UUID>,
    ): Either<ClubTaxonomiaError, Result> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.LIST)) {
                auditor.denegado(actor, Resource.GROUP, Action.LIST)
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val required = tagValueIds.mapTo(linkedSetOf()) { TagValueId.of(it) }

            val taxonomy = taxonomyRepository.findByClub(clubId)
            ensureAssignableFilter(taxonomy, required)
            Result(
                members = groupRepository.previewMembers(clubId, required),
                filterRequiresHealthData = required.any { it in taxonomy.healthDataValueIds() },
            )
        }
}
