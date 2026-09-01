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
     * El reporte del propio alumno sobre una sesión ejecutada (módulo `seguimiento`, LAL-30): estado, valoración,
     * motivo y notas. Recurso propio, no una acción sobre [RESOLVED_SESSION]: el reporte es su propio agregado con
     * escritura, la sesión resuelta es de solo lectura.
     */
    SESSION_REPORT,

    /**
     * El consentimiento explícito de datos de salud del propio alumno (Art. 9.2.a RGPD, módulo
     * `identidad`, LAL-128). Recurso propio del interesado, no una acción sobre [USER]: la matriz de
     * gestión de usuarios es cosa del ADMIN, esto lo opera el propio alumno sobre sí mismo.
     */
    CONSENT,

    /**
     * La marca del propio alumno en una distancia estándar (módulo `seguimiento`, LAL-31, ADR-0002
     * D7): el mejor tiempo del corredor, privado. Deliberadamente sin fila de ADMIN/ENTRENADOR en la
     * matriz — ni siquiera para lectura agregada: es la barrera técnica que sostiene la privacidad
     * fuerte que exige la historia (ni el entrenador ni el admin ven valores ni contadores).
     */
    MARCA,
}
