package com.runcriticon.identidad.application.ratelimit

/**
 * Dimensiones de rate-limiting de ventana fija. Cada scope resuelve a un juego de bandas en la configuración
 * (`runcriticon.identidad.ratelimit`). Las dos dimensiones anónimas (cuenta e IP) se consultan por separado sobre el
 * mismo flujo; la de invitación es por actor.
 */
enum class RateLimitScope {
    /** Magic link de login, por email solicitante (3/h·10/día). */
    MAGIC_LINK_ACCOUNT,

    /** Magic link de login, por IP de origen (20/h·100/día). */
    MAGIC_LINK_IP,

    /** Reseteo de contraseña, por email solicitante (3/h·5/día). */
    PASSWORD_RESET_ACCOUNT,

    /** Reseteo de contraseña, por IP de origen (20/h·100/día). */
    PASSWORD_RESET_IP,

    /** Invitación / reenvío, por actor (admin o entrenador): 100/h. */
    INVITATION_ACTOR,
}
