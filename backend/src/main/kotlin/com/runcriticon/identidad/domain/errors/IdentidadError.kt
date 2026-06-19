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
}
