package com.runcriticon.seguimiento.domain

import java.util.UUID

/**
 * Identificador tipado del entrenador tal como lo ve este módulo (LAL-116). No expone `new()`: este módulo
 * nunca crea entrenadores, el id es siempre el `userId` del propio [com.runcriticon.shared.autorizacion.model.Principal]
 * cuando el entrenador consulta sus alertas, o el `aggregateId` de `EntrenadorAsignadoAGrupo`/
 * `EntrenadorEliminadoDeGrupo`/`EntrenadorEliminado`.
 */
@JvmInline
value class CoachId(
    val value: UUID,
) {
    companion object {
        fun of(value: UUID): CoachId = CoachId(value)
    }
}
