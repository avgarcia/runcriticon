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

    /** Se intenta reportar un día sin sesión publicada para el alumno (LAL-30). */
    data object SessionNotFound : SeguimientoError()

    /** El alumno no tiene consentimiento vigente de datos de salud (ADR-0014 D18, LAL-128 PR2). Cubre tanto
     * la revocación explícita como la ausencia total de fila — fail-closed, ver `ConsentReader`. */
    data object ConsentNotGranted : SeguimientoError()

    /** El día destino de un `MOVER` (LAL-33) ya tiene una sesión efectiva y la petición no trae
     * `resolucionConflicto`. El alumno decide Reemplazar/Intercambiar/Cancelar antes de reintentar. */
    data object TargetDayOccupied : SeguimientoError()
}
