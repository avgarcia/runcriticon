package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Quita la excepción manual y devuelve la decisión al filtro de tags. El admin y el entrenador.
 *
 * **No comprueba el alumno**, a diferencia de [OverrideGroupMembershipCommand]: una excepción sobre alguien que ya no
 * existe, que dejó de ser alumno o que nunca debió tenerla es justo lo que hay que poder limpiar; exigir que siga
 * siendo alumno del club dejaría filas imposibles de borrar. Sí comprueba el grupo, que es la frontera de club.
 *
 * Idempotente: no distingue si había excepción o no. Quitar lo que no está deja el mismo estado, así que devolver un
 * 404 por ello sería contar algo del estado sin necesidad.
 *
 * **Ahora publica** `MembresiaDeGrupoCambiada` (antes no publicaba nada, LAL-94): con el snapshot completo ya no
 * hace falta saber si el alumno queda dentro o fuera del grupo para decidir qué evento emitir -- se resuelve la
 * membresía tal cual queda y se publica, sea cual sea el resultado.
 */
@ApplicationService
class ClearGroupMembershipOverrideCommand(
    private val groupRepository: GroupRepository,
    private val groupMembershipPublisher: GroupMembershipPublisher,
) {
    @Transactional
    fun execute(
        actor: Principal,
        groupId: UUID,
        studentId: UUID,
    ): Either<ClubTaxonomiaError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.UPDATE)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val group = GroupId.of(groupId)

            ensureGroupOfClub(groupRepository, clubId, group)

            // El número de filas borradas se descarta aquí: es lo que hace que la operación sea idempotente de cara
            // a quien llama, que no tiene por qué saber si existía la excepción.
            groupRepository.deleteOverride(clubId, group, PersonId.of(studentId))
            groupMembershipPublisher.publishFor(clubId, actor.userId, setOf(group))
            Unit
        }
}
