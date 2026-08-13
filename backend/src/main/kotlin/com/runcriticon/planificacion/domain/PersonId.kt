package com.runcriticon.planificacion.domain

import java.util.UUID

/**
 * Identificador tipado de una persona (alumno o entrenador) tal como la ve este módulo.
 *
 * No expone `new()`, mismo criterio que `clubtaxonomia.domain.person.PersonId`: este módulo **nunca crea
 * personas**. El id llega en el `aggregateId` de los eventos de `club_taxonomia` (`AlumnoAsignadoAGrupo`,
 * `EntrenadorAsignadoAGrupo`, ...) o como `entrenadorId` en la petición de creación de plan, así que la única
 * vía de construcción es [of].
 *
 * Un único tipo para ambos roles, no `StudentId`/`CoachId` separados: mismo criterio que el `PersonId` real de
 * `clubtaxonomia` (`GroupRepository.kt` usa `studentId: PersonId` y `coachId: PersonId` indistintamente) — el
 * rol lo da el contexto de uso, no el tipo.
 */
@JvmInline
value class PersonId(
    val value: UUID,
) {
    companion object {
        fun of(value: UUID): PersonId = PersonId(value)
    }
}
