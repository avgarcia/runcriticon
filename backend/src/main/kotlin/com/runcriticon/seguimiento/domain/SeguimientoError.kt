package com.runcriticon.seguimiento.domain

/**
 * Errores de dominio y de guardado de este módulo. Sealed class propia, sin núcleo compartido de errores — mismo
 * criterio que `PlanificacionError`/`ClubTaxonomiaError` (ADR-0008).
 */
sealed class SeguimientoError {
    data object Forbidden : SeguimientoError()

    data class InvalidInput(
        val field: String,
        val reason: String,
    ) : SeguimientoError()
}
