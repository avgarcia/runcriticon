package com.runcriticon.seguimiento.domain

import java.util.UUID

/**
 * Identificador tipado del alumno tal como lo ve este módulo.
 *
 * No expone `new()`, mismo criterio que `planificacion.domain.PersonId`: este módulo nunca crea alumnos. El id
 * llega en `snapshotAlumnos` de `PlanPublicado` o es el `userId` del propio [com.runcriticon.shared.autorizacion.model.Principal]
 * cuando el alumno consulta su plan.
 */
@JvmInline
value class StudentId(
    val value: UUID,
) {
    companion object {
        fun of(value: UUID): StudentId = StudentId(value)
    }
}
