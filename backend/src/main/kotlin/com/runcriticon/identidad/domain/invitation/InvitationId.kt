package com.runcriticon.identidad.domain.invitation

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * Identificador tipado de la invitación.
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
