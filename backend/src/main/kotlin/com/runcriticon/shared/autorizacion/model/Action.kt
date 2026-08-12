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

    /**
     * Crear un recurso propio del club que no es una persona ni un catálogo, ej. un grupo. Distinta de [INVITE], que
     * da de alta a alguien por email, y de [MANAGE], que gobierna un agregado completo con todas sus mutaciones.
     */
    CREATE,

    /** Gestionar —crear, renombrar o archivar— un recurso compuesto, ej. la taxonomía del club. */
    MANAGE,

    /**
     * Consultar y cambiar la clasificación de una persona: qué valores de la taxonomía tiene asignados. Es distinta
     * de [MANAGE] sobre la taxonomía, que gobierna el catálogo de ejes del club: aquí solo se usan las etiquetas que
     * ese catálogo ya ofrece.
     */
    CLASSIFY,

    /**
     * Vincular o desvincular un entrenador de un grupo. Deliberadamente distinta de [UPDATE] sobre un grupo (que sí
     * comparten ADMIN y ENTRENADOR sobre las excepciones manuales de alumnos): esta relación decidirá quién puede
     * publicar planes al grupo, así que concederla no puede quedar en manos de quien la recibiría.
     */
    ASSIGN_COACH,
}
