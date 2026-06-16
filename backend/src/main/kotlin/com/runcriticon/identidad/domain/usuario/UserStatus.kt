package com.runcriticon.identidad.domain.usuario

/**
 * Estado del ciclo de vida de la cuenta (ADR-0003 D2). Solo una cuenta ACTIVO puede autenticarse.
 * Los valores en mayúsculas son los que se persisten en SQL (columna `estado`).
 */
enum class UserStatus { INVITADO, ACTIVO, DESACTIVADO }
