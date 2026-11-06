package com.runcriticon.seguimiento.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de una operación de reajuste de día. Correlaciona las filas que escribe una misma operación
 * (un `REEMPLAZAR`/`INTERCAMBIAR` escribe dos [DayAdjustment] con el mismo id) para poder deshacerla de una vez.
 */
@JvmInline
value class OperationId(
    val value: UUID,
) {
    companion object {
        fun new(): OperationId = OperationId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): OperationId = OperationId(value)
    }
}
