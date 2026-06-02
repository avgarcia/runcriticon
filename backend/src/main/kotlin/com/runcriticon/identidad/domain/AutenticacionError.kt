package com.runcriticon.identidad.domain

/**
 * Errores de autenticación (ADR-0003 D5). Los mensajes que llegan al cliente son **neutros**: no
 * distinguen "email no existe" de "contraseña incorrecta" para no permitir enumerar cuentas.
 */
sealed class AutenticacionError {
    data object CredencialesInvalidas : AutenticacionError()

    data object CuentaNoActiva : AutenticacionError()
}
