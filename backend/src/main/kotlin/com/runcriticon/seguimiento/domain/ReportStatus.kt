package com.runcriticon.seguimiento.domain

/**
 * Estado de un reporte de sesión (LAL-30), tal como lo define `docs/glosario.md` §Seguimiento. Valores en
 * castellano por ser un enum **persistido** (ADR-0008 D4), mismo criterio que `SessionType`.
 */
enum class ReportStatus {
    HECHO,
    PARCIAL,
    NO_HECHO,
}
