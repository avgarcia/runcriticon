package com.runcriticon.seguimiento.domain

/**
 * Motivo de un reajuste de día (LAL-33, ticket). Catálogo cerrado; valores en castellano por ser un enum
 * **persistido** (ADR-0008 D4).
 *
 * **Enum propio, no una extensión de [NotDoneReason]**: aunque comparten dos valores en la superficie
 * (cansancio, molestias), son conceptos de negocio distintos — "por qué no hiciste la sesión" (reporte,
 * LAL-30) frente a "por qué reajustas el día" (este ticket). Añadir `IMPREVISTO` a `NotDoneReason` cambiaría
 * el contrato ya publicado de LAL-30 (el enum Kotlin, el CHECK de `reporte_sesion`, el JSON Schema de
 * `ReporteRegistrado` y `MiReporteRequest.motivo`) por una feature que no lo necesita.
 *
 * [MOLESTIAS] tiene el mismo efecto colateral que en [SessionReport.create]: [DayAdjustment.create] activa
 * `painFlag` automáticamente, nunca es un input directo del alumno.
 */
enum class AdjustmentReason {
    CANSANCIO,
    MOLESTIAS,
    IMPREVISTO,
}
