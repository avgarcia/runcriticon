package com.runcriticon.clubtaxonomia.domain.person

import com.runcriticon.clubtaxonomia.domain.group.GroupId

/**
 * Un entrenador tal como se pinta en el listado del club: lo mínimo para identificarlo, más los grupos que lleva y
 * cuántos alumnos suman.
 *
 * No reutiliza [Person] a propósito: aquella representa a cualquier persona del club (alumno o entrenador) y no
 * conoce su carga; este es específicamente el resultado de resolver un entrenador con sus grupos asignados, y solo
 * tiene sentido en el contexto de este listado — mismo criterio que [StudentSummary].
 *
 * `groups` viene siempre vacía y `totalStudents` siempre a 0 hasta que exista la asignación entrenador↔grupo
 * (LAL-93): el agregado `Group` deja esa relación fuera de sí mismo a propósito, así que hoy no hay de dónde
 * sacarla. No es una limitación oculta, es la base intencionada sobre la que se construye después.
 */
data class CoachWorkload(
    val id: PersonId,
    val name: String,
    val email: String,
    val status: PersonStatus,
    val groups: List<AssignedGroup>,
    val totalStudents: Int,
)

/** Un grupo que lleva un entrenador, con cuántos alumnos suma. */
data class AssignedGroup(
    val id: GroupId,
    val name: String,
    val totalStudents: Int,
)
