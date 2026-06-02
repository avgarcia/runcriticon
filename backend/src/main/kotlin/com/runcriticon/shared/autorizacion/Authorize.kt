package com.runcriticon.shared.autorizacion

/**
 * Declara la regla de autorización que protege un endpoint de la capa `api` (ADR-0009 D6). La
 * [expresion] referencia la matriz de autorización (ej. `"PLAN:CREAR"`). ArchUnit exige que
 * **todo** handler público lleve [Authorize] o [NoAuthRequired]: ningún endpoint queda sin decisión.
 *
 * El motor que evalúa la expresión llega en Fase 1; en H0 queda el contrato y la verificación
 * estructural.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Authorize(
    val expresion: String,
)

/**
 * Marca un endpoint como deliberadamente público (ej. health check, login). Obliga a justificar
 * por escrito por qué no requiere autorización.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NoAuthRequired(
    val justificacion: String,
)
