package com.runcriticon.seguimiento.domain

/**
 * Motivo de un reajuste de día (ticket). Catálogo cerrado; valores en castellano por ser un enum
 * **persistido** (ADR-0008 D4).
 *
 * **Enum propio, no una extensión de [NotDoneReason]**: aunque comparten dos valores en la superficie
 * (cansancio, molestias), son conceptos de negocio distintos — "por qué no hiciste la sesión" (el reporte de
 * sesión) frente a "por qué reajustas el día" (este ticket). Añadir `IMPREVISTO` a `NotDoneReason` cambiaría
 * el contrato ya publicado del reporte de sesión (el enum Kotlin, el CHECK de `reporte_sesion`, el JSON
 * Schema de `ReporteRegistrado` y `MiReporteRequest.motivo`) por una feature que no lo necesita.
 *
 * [MOLESTIAS] tiene el mismo efecto colateral que en [SessionReport.create]: [DayAdjustment.create] activa
 * `painFlag` automáticamente, nunca es un input directo del alumno.
 *
 * [LESION] (wireframe 07 §Flujo B opción 4) activa `painFlag` igual que [MOLESTIAS] y solo es
 * válido con [AdjustmentAction.SALTADA] — avisar de lesión no mueve la sesión, la salta. A diferencia del
 * resto de motivos, dispara además el evento de integración [com.runcriticon.seguimiento.api.events.LesionDeclarada]
 * hacia `clubtaxonomia` cuando el alumno confirma el cambio de su tag `estado` (`RescheduleDayCommand`).
 */
enum class AdjustmentReason {
    CANSANCIO,
    MOLESTIAS,
    IMPREVISTO,
    LESION,
}
