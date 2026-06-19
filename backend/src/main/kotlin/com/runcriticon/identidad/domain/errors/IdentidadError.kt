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
}
