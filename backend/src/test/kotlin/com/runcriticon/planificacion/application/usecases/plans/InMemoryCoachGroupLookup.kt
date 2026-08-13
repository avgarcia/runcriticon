package com.runcriticon.planificacion.application.usecases.plans

import com.runcriticon.planificacion.application.ports.outbound.persistence.CoachGroupLookup
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.tenancy.ClubId

/** Doble en memoria de [CoachGroupLookup], mismo patrón que `InMemoryCoachLookup` de `clubtaxonomia`. */
class InMemoryCoachGroupLookup(
    private val coachesOfGroup: Set<Pair<PersonId, GroupId>> = emptySet(),
) : CoachGroupLookup {
    val calls = mutableListOf<Triple<ClubId, PersonId, GroupId>>()

    override fun isCoachOfGroup(
        clubId: ClubId,
        coachId: PersonId,
        groupId: GroupId,
    ): Boolean {
        calls += Triple(clubId, coachId, groupId)
        return (coachId to groupId) in coachesOfGroup
    }
}
