package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
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
 */
@ApplicationService
class PreviewGroupMembersQuery(
    private val taxonomyRepository: TaxonomyRepository,
    private val groupRepository: GroupRepository,
) {
    @Transactional(readOnly = true)
    fun execute(
        actor: Principal,
        tagValueIds: List<UUID>,
    ): Either<ClubTaxonomiaError, GroupMembers> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.LIST)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val required = tagValueIds.mapTo(linkedSetOf()) { TagValueId.of(it) }

            ensureAssignableFilter(taxonomyRepository.findByClub(clubId), required)
            groupRepository.previewMembers(clubId, required)
        }
}
