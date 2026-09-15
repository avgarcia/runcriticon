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

    /**
     * Copia defensiva, no la referencia viva del mapa: [add] y [remove] mutan el `MutableSet` en el sitio, y
     * `StudentClassification.classify` guarda el resultado de esta llamada en `before` para compararlo con `after`
     * tras la escritura. Sin la copia, `before` sería el mismo objeto que `after` una vez mutado y toda comparación
     * `before` vs `after` saldría vacía — silenciando tanto el recálculo de membresía de grupo como el asiento de
     * auditoría para `Assign`/`Unassign`. La base de datos real (`StudentTagRepositoryJdbc`) no tiene este problema:
     * cada `SELECT` materializa un `Set` nuevo.
     */
    override fun findAssignedValueIds(
        clubId: ClubId,
        studentId: PersonId,
    ): Set<TagValueId> = assignments[studentId]?.toSet() ?: emptySet()

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

    override fun countStudentsWithAnyValue(
        clubId: ClubId,
        valueIds: Set<TagValueId>,
    ): Int = assignments.count { (_, values) -> values.any { it in valueIds } }

    /**
     * Copia defensiva **por entrada**, mismo motivo que [findAssignedValueIds]: si `before` y `after` compartieran el
     * `MutableSet` de un mismo alumno, su Δ saldría vacía tras [addToAll]/[removeFromAll] y se silenciarían a la vez
     * la auditoría, los eventos de grupo y el recuento devuelto para ese alumno.
     */
    override fun findAssignedValueIdsByStudent(
        clubId: ClubId,
        studentIds: Set<PersonId>,
    ): Map<PersonId, Set<TagValueId>> =
        studentIds.mapNotNull { studentId -> assignments[studentId]?.let { studentId to it.toSet() } }.toMap()

    /** Una sola escritura registrada, aunque toque a varios alumnos: es una operación, no `studentIds.size`. */
    override fun addToAll(
        clubId: ClubId,
        studentIds: Set<PersonId>,
        valueId: TagValueId,
    ): Int {
        var inserted = 0
        studentIds.forEach { studentId ->
            val values = assignments.getOrPut(studentId) { mutableSetOf() }
            if (values.add(valueId)) inserted++
        }
        writeCount++
        return inserted
    }

    override fun removeFromAll(
        clubId: ClubId,
        studentIds: Set<PersonId>,
        valueId: TagValueId,
    ): Int {
        var removed = 0
        studentIds.forEach { studentId ->
            if (assignments[studentId]?.remove(valueId) == true) removed++
        }
        writeCount++
        return removed
    }
}
