package com.runcriticon.seguimiento.domain

/**
 * Motivo de un reporte en estado [ReportStatus.NO_HECHO] (LAL-30, `docs/glosario.md` §Seguimiento). Catálogo
 * cerrado; valores en castellano por ser un enum **persistido** (ADR-0008 D4).
 *
 * [MOLESTIAS] tiene un efecto colateral en [SessionReport.create]: activa `painFlag` automáticamente, sin que
 * el alumno tenga que marcarlo aparte — así lo fija el glosario.
 */
enum class NotDoneReason {
    CANSANCIO,
    TRABAJO,
    VIAJE,
    ENFERMEDAD,
    SIN_TIEMPO,
    MOLESTIAS,
    OTRA,
}
