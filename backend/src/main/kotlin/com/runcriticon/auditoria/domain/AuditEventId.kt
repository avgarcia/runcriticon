package com.runcriticon.auditoria.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de una fila de `auditoria.evento`.
 */
@JvmInline
value class AuditEventId(
    val value: UUID,
) {
    companion object {
        fun new(): AuditEventId = AuditEventId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): AuditEventId = AuditEventId(value)
    }
}
