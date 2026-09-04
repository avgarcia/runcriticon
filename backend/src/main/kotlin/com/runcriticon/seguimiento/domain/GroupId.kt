package com.runcriticon.seguimiento.domain

import java.util.UUID

/**
 * Identificador tipado del grupo tal como lo ve este módulo (LAL-116). Propio de `seguimiento`, no el
 * `GroupId` de `planificacion`: cada módulo tiene su propio tipo aunque referencien el mismo grupo real de
 * `club_taxonomia` — ADR-0007 prohíbe compartir tipos de dominio entre módulos.
 *
 * No expone `new()`: este módulo nunca crea grupos, el id llega en `PlanPublicado.grupoId` o en los eventos
 * `EntrenadorAsignadoAGrupo`/`EntrenadorEliminadoDeGrupo` de `club_taxonomia`.
 */
@JvmInline
value class GroupId(
    val value: UUID,
) {
    companion object {
        fun of(value: UUID): GroupId = GroupId(value)
    }
}
