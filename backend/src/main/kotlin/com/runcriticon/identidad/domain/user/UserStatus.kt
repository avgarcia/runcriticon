package com.runcriticon.identidad.domain.user

/**
 * Estado del ciclo de vida de la cuenta. Solo una cuenta ACTIVO puede autenticarse. Los valores en mayúsculas son los
 * que se persisten en SQL (columna `estado`).
 */
enum class UserStatus { INVITADO, ACTIVO, DESACTIVADO }
