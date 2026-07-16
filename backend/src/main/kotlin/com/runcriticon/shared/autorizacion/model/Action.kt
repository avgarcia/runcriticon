package com.runcriticon.shared.autorizacion.model

/**
 * Acción que un [Role] puede ejercer sobre un [Resource].
 */
enum class Action {
    /** Invitar (dar de alta) un recurso de identidad. */
    INVITE,

    /** Listar los recursos de un tipo (ej. el admin lista los entrenadores del club). */
    LIST,

    /** Revocar todas las sesiones activas de un usuario (logout forzado por admin). */
    REVOKE_SESSIONS,

    /** Desactivar la cuenta de un usuario (pasa a `DESACTIVADO`). */
    DEACTIVATE,
}
