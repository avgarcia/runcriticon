package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.observability.OpenTelemetryHelper
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Resuelve y publica un [MembresiaDeGrupoCambiada] por cada grupo de [groupIds], dentro de la transacción del
 * llamante. Colaborador compartido de los seis puntos de emisión (crear grupo, overrides, cambios de tags de un
 * alumno) — mismo motivo que [com.runcriticon.clubtaxonomia.application.usecases.studenttags.StudentClassification]
 * para no repetir la fontanería en cada caso de uso.
 *
 * Cada grupo se resuelve y publica por separado, no en lote: son eventos independientes, uno por `aggregateId`.
 */
@Component
class GroupMembershipPublisher(
    private val groupRepository: GroupRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun publishFor(
        clubId: ClubId,
        actorId: UUID?,
        groupIds: Set<GroupId>,
    ) {
        groupIds.forEach { groupId ->
            publish(clubId, actorId, groupId, groupRepository.resolveMembers(clubId, groupId))
        }
    }

    /**
     * Publica directamente con una membresía ya resuelta, sin repetir la consulta -- para el único punto de emisión
     * que ya la tiene a mano ([OverrideGroupMembershipCommand], que acaba de pedir `findDetail` para devolverlo).
     */
    fun publish(
        clubId: ClubId,
        actorId: UUID?,
        groupId: GroupId,
        members: Set<PersonId>,
    ) {
        eventPublisher.publishEvent(
            MembresiaDeGrupoCambiada(
                eventId = UuidCreator.getTimeOrderedEpoch(),
                aggregateId = groupId.value,
                occurredAt = Instant.now(),
                clubId = clubId.value,
                actorId = actorId,
                traceparent = OpenTelemetryHelper.actualTraceparent(),
                alumnos = members.map { it.value },
            ),
        )
    }
}
