package com.runcriticon.planificacion.domain

import java.util.UUID

/**
 * Identificador tipado de un grupo tal como lo ve este módulo.
 *
 * No expone `new()`, mismo criterio que [PersonId]: este módulo **nunca crea grupos** — el id llega en el
 * `groupId` de los eventos de `club_taxonomia` o en la petición de creación de plan, así que la única vía de
 * construcción es [of].
 */
@JvmInline
value class GroupId(
    val value: UUID,
) {
    companion object {
        fun of(value: UUID): GroupId = GroupId(value)
    }
}
