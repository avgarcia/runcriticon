package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.application.usecases.HEALTH_TAG_RESOURCE
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupMemberOrigin
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
 * La composición actual de un grupo: quién está dentro, por qué, y a quién se ha sacado a mano. El admin y el
 * entrenador, con la misma acción que el listado —leer un grupo suelto no es un permiso distinto de leer la lista.
 *
 * Un grupo que no existe y uno de otro club dan el mismo `GroupNotFound`: el repositorio devuelve `null` en ambos casos
 * y aquí no se distinguen, para no permitir enumerar grupos ajenos (excepción anti-enumeración de ADR-0009 D14).
 *
 * `@AuditAccess` (ADR-0009 D15): si el filtro del grupo exige un valor de salud (`lesión`, `post-parto`), su
 * composición revela ese dato y se audita a quien queda expuesto: los miembros que están por cumplir el filtro y los
 * excluidos a mano que lo cumplen. Los incluidos a mano no — están por la excepción, no por llevar el tag.
 */
@ApplicationService
class GetGroupDetailQuery(
    private val groupRepository: GroupRepository,
    private val taxonomyRepository: TaxonomyRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    /** La composición del grupo; [auditSubjectIds] según el KDoc de la clase. */
    data class Result(
        val detail: GroupDetail,
        private val filterRequiresHealthData: Boolean,
    ) : AuditSubjects {
        override fun auditSubjectIds(): Set<UUID> {
            if (!filterRequiresHealthData) return emptySet()
            val byFilter = detail.members.filter { it.origin == GroupMemberOrigin.FILTER }.map { it.member.id.value }
            val excludedMatching = detail.exclusions.filter { it.matchesFilter }.map { it.member.id.value }
            return (byFilter + excludedMatching).toSet()
        }
    }

    @AuditAccess(type = AccessType.SALUD, resource = HEALTH_TAG_RESOURCE)
    @Transactional
    fun execute(
        actor: Principal,
        groupId: UUID,
    ): Either<ClubTaxonomiaError, Result> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.LIST)) {
                auditor.denegado(actor, Resource.GROUP, Action.LIST)
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)

            val detail =
                groupRepository.findDetail(clubId, GroupId.of(groupId)) ?: raise(ClubTaxonomiaError.GroupNotFound)
            val healthValueIds = taxonomyRepository.findByClub(clubId).healthDataValueIds()
            Result(detail, filterRequiresHealthData = detail.group.requiredTagValueIds.any { it in healthValueIds })
        }
}
