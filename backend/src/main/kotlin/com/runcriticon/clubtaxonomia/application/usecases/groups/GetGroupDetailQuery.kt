package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * La composición actual de un grupo: quién está dentro, por qué, y a quién se ha sacado a mano. El admin y el
 * entrenador, con la misma acción que el listado —leer un grupo suelto no es un permiso distinto de leer la lista.
 *
 * Un grupo que no existe y uno de otro club dan el mismo `GroupNotFound`: el repositorio devuelve `null` en ambos casos
 * y aquí no se distinguen, para no permitir enumerar grupos ajenos.
 */
@ApplicationService
class GetGroupDetailQuery(
    private val groupRepository: GroupRepository,
) {
    @Transactional(readOnly = true)
    fun execute(
        actor: Principal,
        groupId: UUID,
    ): Either<ClubTaxonomiaError, GroupDetail> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.LIST)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)

            groupRepository.findDetail(clubId, GroupId.of(groupId)) ?: raise(ClubTaxonomiaError.GroupNotFound)
        }
}
