package com.runcriticon.identidad.domain.events

import com.runcriticon.identidad.domain.user.User
import java.time.Instant
import java.util.UUID

/**
 * Domain event interno: un usuario ha sido invitado. El caso de uso del módulo lo recoge y lo traduce al integration
 * event correspondiente ([com.runcriticon.identidad.api.events.AlumnoInvitado]) cuando el rol invitado lo requiere.
 */
data class UserInvited(
    val eventId: UUID,
    val occurredAt: Instant,
    val user: User,
    val actorId: UUID,
)
