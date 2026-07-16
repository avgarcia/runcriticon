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
}
