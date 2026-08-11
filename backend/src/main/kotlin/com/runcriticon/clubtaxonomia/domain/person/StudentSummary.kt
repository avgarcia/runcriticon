package com.runcriticon.clubtaxonomia.domain.person

import com.runcriticon.clubtaxonomia.domain.tag.TagValueId

/**
 * Un alumno tal como se pinta en el listado del club: lo mínimo para identificarlo y filtrarlo, más **todos** los
 * valores de taxonomía que tiene asignados — no solo los que coinciden con un filtro aplicado, porque la fila necesita
 * pintar todos sus chips.
 *
 * No reutiliza [Person] a propósito: aquella representa a cualquier persona del club (alumno o entrenador) y no
 * conoce sus tags; este es específicamente el resultado de resolver un alumno con su clasificación, y solo tiene
 * sentido en el contexto de un listado.
 */
data class StudentSummary(
    val id: PersonId,
    val name: String,
    val email: String,
    val status: PersonStatus,
    val tagValueIds: Set<TagValueId>,
)
