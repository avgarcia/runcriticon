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

    /**
     * Eliminar físicamente un recurso y los datos personales que cuelgan de él, propagando la baja al resto de
     * módulos. Es irreversible, a diferencia de [DEACTIVATE], que solo cambia el estado de la cuenta.
     */
    DELETE,

    /** Actualizar un recurso existente (ej. el nombre del club). */
    UPDATE,

    /** Gestionar —crear, renombrar o archivar— un recurso compuesto, ej. la taxonomía del club. */
    MANAGE,
}
