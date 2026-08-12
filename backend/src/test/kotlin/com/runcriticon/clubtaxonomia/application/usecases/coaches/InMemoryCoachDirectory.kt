package com.runcriticon.clubtaxonomia.application.usecases.coaches

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.CoachDirectory
import com.runcriticon.clubtaxonomia.domain.person.CoachWorkload
import com.runcriticon.shared.tenancy.ClubId

/**
 * Doble en memoria del puerto. Además de devolver lo configurado, registra con qué club se le llamó: es lo que hay
 * que comprobar en el caso de uso, no el SQL de resolución (ya cubierto contra Postgres real).
 */
class InMemoryCoachDirectory(
    private val coaches: List<CoachWorkload> = emptyList(),
) : CoachDirectory {
    val calls: MutableList<ClubId> = mutableListOf()

    override fun listByClub(clubId: ClubId): List<CoachWorkload> {
        calls += clubId
        return coaches
    }
}
