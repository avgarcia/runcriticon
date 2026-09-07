package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Persistencia de la clasificación de los alumnos: qué valores de la taxonomía tiene asignados cada uno.
 *
 * Trabaja con **ids sueltos**, no con el modelo de lectura: componer la clasificación ordenada es cosa de
 * [com.runcriticon.clubtaxonomia.domain.studenttags.StudentTags], que necesita la taxonomía y este puerto no la
 * conoce.
 */
interface StudentTagRepository {
    /** Valores que el alumno tiene asignados ahora mismo. Conjunto vacío si no está clasificado. */
    fun findAssignedValueIds(
        clubId: ClubId,
        studentId: PersonId,
    ): Set<TagValueId>

    /**
     * Deja al alumno exactamente con [valueIds]: quita lo que sobra y añade lo que falta. Conjunto vacío borra toda su
     * clasificación.
     *
     * Aplica la diferencia en vez de borrar e insertar todo, para no reescribir la fecha de las asignaciones que no
     * cambian: es la única traza de cuándo se clasificó al alumno, y guardar el formulario la destruiría.
     */
    fun replace(
        clubId: ClubId,
        studentId: PersonId,
        valueIds: Set<TagValueId>,
    )

    /** Añade un valor sin tocar el resto. Idempotente: si ya estaba asignado, no cambia nada. */
    fun add(
        clubId: ClubId,
        studentId: PersonId,
        valueId: TagValueId,
    )

    /** Quita un valor. Idempotente: si no estaba asignado, no pasa nada. */
    fun remove(
        clubId: ClubId,
        studentId: PersonId,
        valueId: TagValueId,
    )

    /**
     * Alumnos distintos del club que tienen asignado ahora mismo alguno de [valueIds]. Es el aviso de impacto de
     * archivar un eje o un valor (LAL-83): puramente informativo, archivar no borra estas asignaciones (ADR-0002
     * D10). `0` si [valueIds] está vacío.
     */
    fun countStudentsWithAnyValue(
        clubId: ClubId,
        valueIds: Set<TagValueId>,
    ): Int

    /**
     * Lo que tiene asignado ahora mismo cada uno de [studentIds], en una sola consulta.
     *
     * Un alumno sin ninguna asignación **no aparece** en el mapa; el llamador debe rellenarlo con el conjunto vacío.
     * Devolver el mapa incompleto y no completarlo aquí mantiene esta consulta como la lectura pura que es.
     */
    fun findAssignedValueIdsByStudent(
        clubId: ClubId,
        studentIds: Set<PersonId>,
    ): Map<PersonId, Set<TagValueId>>

    /**
     * Añade [valueId] a todos los [studentIds] en una sola sentencia, sin tocar el resto de su clasificación.
     * Idempotente por alumno: quien ya lo tenía no cambia ni pierde la fecha de su asignación.
     *
     * @return cuántas filas se insertaron de verdad.
     */
    fun addToAll(
        clubId: ClubId,
        studentIds: Set<PersonId>,
        valueId: TagValueId,
    ): Int

    /**
     * Quita [valueId] de todos los [studentIds] en una sola sentencia. Idempotente: quien no lo tenía no hace
     * fallar nada.
     *
     * @return cuántas filas se borraron.
     */
    fun removeFromAll(
        clubId: ClubId,
        studentIds: Set<PersonId>,
        valueId: TagValueId,
    ): Int
}
