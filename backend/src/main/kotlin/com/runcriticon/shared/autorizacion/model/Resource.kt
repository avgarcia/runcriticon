package com.runcriticon.shared.autorizacion.model

/**
 * Recurso protegido por la matriz de autorización (ADR-0009 D6). Los valores concretos
 * se añaden por feature en Fase 1.
 */
enum class Resource {
    /** Un entrenador del club (rol `ENTRENADOR`). */
    COACH,

    /** Un alumno del club (rol `ALUMNO`). */
    STUDENT,
}
