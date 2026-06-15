package com.runcriticon.identidad.domain.usuario

/**
 * Estado del ciclo de vida de la cuenta (ADR-0003 D2). Solo una cuenta ACTIVO puede autenticarse.
 */
enum class EstadoUsuario { INVITADO, ACTIVO, DESACTIVADO }
