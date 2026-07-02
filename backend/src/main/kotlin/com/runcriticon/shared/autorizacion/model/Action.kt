package com.runcriticon.shared.autorizacion.model

/**
 * Acción que un [Role] puede ejercer sobre un [Resource] (ADR-0009 D6). Los valores concretos
 * se añaden por feature en Fase 1.
 */
enum class Action {
    /** Invitar (dar de alta) un recurso de identidad. */
    INVITE,

    /** Listar los recursos de un tipo (ej. el admin lista los entrenadores del club, LAL-13). */
    LIST,

    /** Revocar todas las sesiones activas de un usuario (logout forzado por admin, ADR-0003 D11). */
    REVOKE_SESSIONS,

    /** Desactivar la cuenta de un usuario (pasa a `DESACTIVADO`, ADR-0003 D11/D15). */
    DEACTIVATE,
}
