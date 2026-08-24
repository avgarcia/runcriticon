package com.runcriticon.seguimiento.domain

import java.util.UUID

/**
 * Identificador tipado del plan semanal tal como lo ve este módulo — es el `aggregateId` de `PlanPublicado`.
 *
 * Solo `of()`: este módulo nunca crea planes, y lo necesita en la clave primaria de
 * `plan_resuelto_por_alumno` para poder distinguir dos planes de grupos distintos que casualmente resuelven el
 * mismo día para el mismo alumno (un alumno puede pertenecer a más de un grupo — los grupos son consultas sobre
 * tags, no son excluyentes).
 */
@JvmInline
value class PlanId(
    val value: UUID,
) {
    companion object {
        fun of(value: UUID): PlanId = PlanId(value)
    }
}
