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
}
