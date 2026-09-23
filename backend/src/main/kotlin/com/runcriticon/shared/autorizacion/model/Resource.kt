package com.runcriticon.shared.autorizacion.model

/**
 * Recurso protegido por la matriz de autorización.
 */
enum class Resource {
    /** Un entrenador del club (rol `ENTRENADOR`). */
    COACH,

    /** Un alumno del club (rol `ALUMNO`). */
    STUDENT,

    /** Un usuario cualquiera del club, sobre el que el admin ejerce acciones de gestión. */
    USER,

    /** La ficha del propio club del principal. */
    CLUB,

    /** La taxonomía del club: sus ejes (`TagKey`) y los valores de cada eje (`TagValue`). */
    TAXONOMY,

    /** Un grupo del club: la consulta nombrada sobre tags que decide qué alumnos lo componen. */
    GROUP,

    /**
     * Sugerencias de fusión de micro-grupos o de grupos casi duplicados (módulo `club_taxonomia`): ayuda de
     * UX para mantener la taxonomía manejable, no una acción sobre [GROUP] en sí — se lista y se descarta, nunca
     * se crea ni se actualiza a mano.
     */
    GROUP_MERGE_SUGGESTION,

    /** Un plan semanal de un grupo (módulo `planificacion`). */
    PLAN,

    /** El log de auditoría de autorización (módulo `auditoria`, ADR-0009 D17). Consulta forense, solo ADMIN. */
    AUDIT_EVENT,

    /**
     * La sesión ya resuelta de un alumno concreto en `seguimiento.plan_resuelto_por_alumno` (módulo
     * `seguimiento`). Distinto de [PLAN]: ese es el plan en construcción del entrenador; este es la vista de
     * solo lectura del propio alumno sobre lo ya publicado — concederle [PLAN] le abriría también `GET
     * /planes`, que es del entrenador.
     */
    RESOLVED_SESSION,

    /**
     * El reporte del propio alumno sobre una sesión ejecutada (módulo `seguimiento`): estado, valoración,
     * motivo y notas. Recurso propio, no una acción sobre [RESOLVED_SESSION]: el reporte es su propio agregado con
     * escritura, la sesión resuelta es de solo lectura.
     */
    SESSION_REPORT,

    /**
     * El consentimiento explícito de datos de salud del propio alumno (Art. 9.2.a RGPD, módulo
     * `identidad`). Recurso propio del interesado, no una acción sobre [USER]: la matriz de
     * gestión de usuarios es cosa del ADMIN, esto lo opera el propio alumno sobre sí mismo.
     */
    CONSENT,

    /**
     * La marca del propio alumno en una distancia estándar (módulo `seguimiento`, ADR-0002
     * D7): el mejor tiempo del corredor, privado. Deliberadamente sin fila de ADMIN/ENTRENADOR en la
     * matriz — ni siquiera para lectura agregada: es la barrera técnica que sostiene la privacidad
     * fuerte que exige la historia (ni el entrenador ni el admin ven valores ni contadores).
     */
    MARCA,

    /**
     * El reajuste del propio alumno sobre el día de una sesión (módulo `seguimiento`): moverla o
     * marcarla como saltada. Recurso propio, no una acción sobre [RESOLVED_SESSION] ni [SESSION_REPORT]: es
     * su propio agregado con escritura ([DAY_ADJUSTMENT] no participa en el reporte de si la sesión se hizo).
     */
    DAY_ADJUSTMENT,

    /**
     * El panel de alertas por excepción del entrenador (módulo `seguimiento`): dolor reportado,
     * ausencia prolongada y ritmo fuera de objetivo, computado sobre [SESSION_REPORT]/[RESOLVED_SESSION] de
     * los alumnos de sus grupos. Deliberadamente sin fila de ADMIN: el ticket lo pide "como entrenador",
     * mismo criterio que [PLAN]. Nunca debe derivar de [MARCA] — ver el KDoc de esa entrada.
     */
    COACH_ALERT,

    /**
     * La vista agregada de salud del club (módulo `seguimiento`): por grupo, cuántos alumnos tiene, si
     * tiene entrenador y cuándo fue el último reporte de alguno de sus alumnos. Solo ADMIN.
     *
     * No reutiliza [COACH_ALERT], que es deliberadamente del ENTRENADOR y devuelve alumnos concretos;
     * esta es del ADMIN y nunca baja del grupo. Nunca debe derivar de [MARCA] — ver el KDoc de esa entrada.
     */
    CLUB_HEALTH,
}
