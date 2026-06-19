package com.runcriticon.identidad.domain.invitation

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de la invitación (ADR-0008: typed IDs como value class sobre UUID v7).
 * El v7 es ordenable por tiempo, lo que ayuda a la localidad de índices en Postgres.
 */
@JvmInline
value class InvitationId(
    val value: UUID,
) {
    companion object {
        fun new(): InvitationId = InvitationId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): InvitationId = InvitationId(value)
    }
}
