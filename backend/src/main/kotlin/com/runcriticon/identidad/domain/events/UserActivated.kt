package com.runcriticon.identidad.domain.events

import com.runcriticon.identidad.domain.user.User
import java.time.Instant
import java.util.UUID

/**
 * Domain event interno: una cuenta ha pasado a `ACTIVO`. El caso de uso del módulo lo recoge y lo traduce al
 * integration event correspondiente según el rol ([com.runcriticon.identidad.api.events.AlumnoActivado] /
 * [com.runcriticon.identidad.api.events.EntrenadorActivado]).
 */
data class UserActivated(
    val eventId: UUID,
    val occurredAt: Instant,
    val user: User,
)
