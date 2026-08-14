package com.runcriticon.planificacion.application.usecases.plans

import com.runcriticon.planificacion.application.ports.outbound.persistence.GroupMembersProjection
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant
import java.util.UUID

/**
 * Doble en memoria de [GroupMembersProjection] centrado en [findStudents] — es lo único que necesita
 * [PublishPlanCommandTest]; `replaceStudents`/`upsert`/`remove` no se ejercen desde este caso de uso.
 */
class InMemoryGroupMembersProjection(
    private val studentsByGroup: Map<GroupId, Set<PersonId>> = emptyMap(),
) : GroupMembersProjection {
    override fun replaceStudents(
        clubId: ClubId,
        groupId: GroupId,
        students: Set<PersonId>,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean = error("no usado en PublishPlanCommandTest")

    override fun upsert(
        clubId: ClubId,
        groupId: GroupId,
        personId: PersonId,
        role: String,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean = error("no usado en PublishPlanCommandTest")

    override fun remove(
        clubId: ClubId,
        groupId: GroupId,
        personId: PersonId,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean = error("no usado en PublishPlanCommandTest")

    override fun findStudents(
        clubId: ClubId,
        groupId: GroupId,
    ): Set<PersonId> = studentsByGroup[groupId] ?: emptySet()
}
