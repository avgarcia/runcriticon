package com.runcriticon.seguimiento.domain

/**
 * Qué hace un reajuste de día (LAL-33) con la sesión de origen. Catálogo cerrado; valores en castellano por
 * ser un enum **persistido** (ADR-0008 D4), mismo criterio que [ReportStatus].
 */
enum class AdjustmentAction {
    /** La sesión se traslada a [DayAdjustment.targetDay]. */
    MOVIDA,

    /** La sesión se marca como saltada, sin trasladarse a ningún otro día. */
    SALTADA,
}
