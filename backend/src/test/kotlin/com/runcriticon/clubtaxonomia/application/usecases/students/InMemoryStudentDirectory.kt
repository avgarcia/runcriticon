package com.runcriticon.clubtaxonomia.application.usecases.students

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentDirectory
import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Doble en memoria del puerto. Además de devolver lo configurado, registra con qué club y con qué filtro se le llamó:
 * es lo que hay que comprobar en el caso de uso, no el SQL de resolución (ya cubierto contra Postgres real).
 */
class InMemoryStudentDirectory(
    private val students: List<StudentSummary> = emptyList(),
) : StudentDirectory {
    val calls: MutableList<Pair<ClubId, Set<TagValueId>>> = mutableListOf()

    override fun listByClub(
        clubId: ClubId,
        requiredTagValueIds: Set<TagValueId>,
    ): List<StudentSummary> {
        calls += clubId to requiredTagValueIds
        return students
    }
}
