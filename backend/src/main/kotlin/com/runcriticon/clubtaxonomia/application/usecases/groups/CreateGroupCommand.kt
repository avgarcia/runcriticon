package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
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
 * Crea un grupo del club: un nombre y el filtro de tags que decide quién entra. El admin y el entrenador.
 *
 * **Valida el nombre antes que los tags.** Con un nombre en blanco y un valor caducado a la vez gana el error del
 * nombre, que es lo que quien está delante puede arreglar sin salir del formulario; un id de tag inválido suele
 * venir de una pantalla desactualizada y se resuelve recargando.
 *
 * Un filtro vacío es válido: no significa "todo el club", sino un grupo al que solo se entrará por inclusión
 * manual. Los ids repetidos se colapsan, porque el dominio guarda un conjunto.
 */
@ApplicationService
class CreateGroupCommand(
    private val taxonomyRepository: TaxonomyRepository,
    private val groupRepository: GroupRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        rawName: String,
        requiredTagValueIds: List<UUID>,
    ): Either<ClubTaxonomiaError, Group> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.CREATE)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val required = requiredTagValueIds.mapTo(linkedSetOf()) { TagValueId.of(it) }

            val group = Group.create(clubId, rawName, required).bind()
            ensureAssignableFilter(taxonomyRepository.findByClub(clubId), required)

            groupRepository.save(clubId, group)
            group
        }
}
