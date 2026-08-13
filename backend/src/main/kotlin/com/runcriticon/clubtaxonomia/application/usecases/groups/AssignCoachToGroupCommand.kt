package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.CoachLookup
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupCoach
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
 * Vincula a un entrenador con un grupo. **Solo el ADMIN**: es la relación que decidirá quién puede publicar planes
 * al grupo (AC2 de LAL-93, pendiente de Planificación), así que concederla no puede quedar en manos de quien la
 * recibiría — a diferencia de las excepciones manuales de alumnos ([OverrideGroupMembershipCommand]), que sí
 * comparten ADMIN y ENTRENADOR.
 *
 * Idempotente: asignar dos veces al mismo entrenador deja el mismo estado.
 *
 * **El orden de las guardas** es el mismo que fija [OverrideGroupMembershipCommand]: primero el grupo, porque es el
 * recurso de la ruta padre y con ambos inválidos manda su 404; la comprobación del entrenador va con
 * [CoachLookup.isCoach] y no con un `SELECT` cualquiera, porque toma un bloqueo que evita la misma condición de
 * carrera con una supresión concurrente que ya documenta [StudentLookup].
 */
@ApplicationService
class AssignCoachToGroupCommand(
    private val groupRepository: GroupRepository,
    private val coachLookup: CoachLookup,
) {
    @Transactional
    fun execute(
        actor: Principal,
        groupId: UUID,
        coachId: UUID,
    ): Either<ClubTaxonomiaError, List<GroupCoach>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.ASSIGN_COACH)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val group = GroupId.of(groupId)
            val coach = PersonId.of(coachId)

            ensureGroupOfClub(groupRepository, clubId, group)
            ensure(coachLookup.isCoach(clubId, coach)) { ClubTaxonomiaError.CoachNotFound }

            groupRepository.assignCoach(clubId, group, coach)

            // Se devuelve la lista ya recalculada, en la misma transacción, mismo criterio que
            // OverrideGroupMembershipCommand devuelve el GroupDetail recalculado.
            groupRepository.findCoaches(clubId, group)
        }
}
