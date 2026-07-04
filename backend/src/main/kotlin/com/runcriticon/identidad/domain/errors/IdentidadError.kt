package com.runcriticon.identidad.domain.errors

/**
 * Errores del módulo identidad (convención `XxxError` por módulo, CLAUDE.md / ADR-0008).
 *
 * Variantes de autenticación (ADR-0003 D5): los mensajes que llegan al cliente son **neutros**:
 * no distinguen "email no existe" de "contraseña incorrecta" para no permitir enumerar cuentas.
 */
sealed class IdentidadError {
    data object InvalidCredentials : IdentidadError()

    data object AccountNotActive : IdentidadError()

    /** El principal no tiene permiso para ejecutar la acción (ADR-0009; default deny de la matriz). */
    data object Forbidden : IdentidadError()

    /** Recurso no encontrado o no pertenece al club del principal (ej. entrenador inexistente). */
    data object NotFound : IdentidadError()

    /**
     * Entrada inválida del cliente: p. ej. una invitación caducada o un token que no coincide
     * (ADR-0003 D4). [field] y [reason] son estables para que la capa REST los traduzca.
     */
    data class InvalidInput(
        val field: String,
        val reason: String,
    ) : IdentidadError()

    /**
     * La operación choca con el estado actual del recurso: p. ej. una invitación ya consumida
     * (un solo uso, ADR-0003 D4).
     */
    data class Conflict(
        val reason: String,
    ) : IdentidadError()

    /**
     * Se ha superado un límite de rate-limiting (ADR-0003 D12). Solo la usan los flujos autenticados
     * (invitar/reenviar): mapea a `429` con `Retry-After`. Los flujos anónimos NO la usan — mantienen
     * su respuesta neutra (202) para no revelar que se alcanzó un límite.
     *
     * @property retryAfterSeconds segundos estimados hasta que vuelva a haber cupo (cabecera Retry-After).
     */
    data class RateLimited(
        val retryAfterSeconds: Long,
    ) : IdentidadError()
}
