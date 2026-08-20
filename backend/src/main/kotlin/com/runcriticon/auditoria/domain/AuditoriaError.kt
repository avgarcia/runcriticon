package com.runcriticon.auditoria.domain

/**
 * Errores del módulo auditoría.
 *
 * Se devuelven como `Either<AuditoriaError, T>` (Raise DSL); el dominio nunca lanza excepción de negocio.
 */
sealed class AuditoriaError {
    /** El rol del llamador no puede consultar el log de auditoría (ADR-0009 D17: solo ADMIN). */
    data object Forbidden : AuditoriaError()

    /** Filtro de entrada inválido, ej. `desde` posterior a `hasta`. */
    data class InvalidInput(
        val field: String,
        val reason: String,
    ) : AuditoriaError()
}
