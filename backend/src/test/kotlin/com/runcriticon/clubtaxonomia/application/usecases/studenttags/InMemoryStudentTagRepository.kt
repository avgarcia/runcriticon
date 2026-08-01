package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Doble en memoria del puerto. Con estado y no un mock, porque los casos de uso leen lo asignado, escriben y vuelven a
 * leer para componer la respuesta: sin estado no se podría comprobar el resultado de dos operaciones encadenadas.
 */
class InMemoryStudentTagRepository(
    private val assignments: MutableMap<PersonId, MutableSet<TagValueId>> = mutableMapOf(),
) : StudentTagRepository {
    /** Cuántas escrituras ha recibido: un rechazo no debe dejar rastro. */
    var writeCount: Int = 0
        private set

    override fun findAssignedValueIds(
        clubId: ClubId,
        studentId: PersonId,
    ): Set<TagValueId> = assignments[studentId].orEmpty()

    override fun replace(
        clubId: ClubId,
        studentId: PersonId,
        valueIds: Set<TagValueId>,
    ) {
        assignments[studentId] = valueIds.toMutableSet()
        writeCount++
    }

    override fun add(
        clubId: ClubId,
        studentId: PersonId,
        valueId: TagValueId,
    ) {
        assignments.getOrPut(studentId) { mutableSetOf() } += valueId
        writeCount++
    }

    override fun remove(
        clubId: ClubId,
        studentId: PersonId,
        valueId: TagValueId,
    ) {
        assignments[studentId]?.remove(valueId)
        writeCount++
    }
}
