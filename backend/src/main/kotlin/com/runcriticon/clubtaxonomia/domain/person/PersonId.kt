package com.runcriticon.clubtaxonomia.domain.person

import java.util.UUID

/**
 * Identificador tipado de una persona del club (alumno o entrenador) tal como la ve este módulo.
 *
 * No expone `new()`, a diferencia del resto de typed IDs del módulo: este módulo **nunca crea personas**. El id es el
 * del usuario en `identidad` y llega en el `aggregateId` del evento de integración, así que la única vía de
 * construcción es [of]. Un `new()` aquí solo permitiría inventar una persona que no existe en su módulo dueño.
 */
@JvmInline
value class PersonId(
    val value: UUID,
) {
    companion object {
        fun of(value: UUID): PersonId = PersonId(value)
    }
}
