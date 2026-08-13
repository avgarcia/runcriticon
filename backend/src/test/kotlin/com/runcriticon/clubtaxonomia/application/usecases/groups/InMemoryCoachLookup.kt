package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.CoachLookup
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.tenancy.ClubId

/** Doble en memoria de [CoachLookup]: `true` solo para los ids configurados como entrenadores. */
class InMemoryCoachLookup(
    private val coaches: Set<PersonId> = emptySet(),
) : CoachLookup {
    val calls: MutableList<Pair<ClubId, PersonId>> = mutableListOf()

    override fun isCoach(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean {
        calls += clubId to personId
        return personId in coaches
    }
}

/** Como [InMemoryCoachLookup] pero siempre `true`, para aislar la autorización del resto de guardas. */
object AlwaysACoach : CoachLookup {
    override fun isCoach(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean = true
}
