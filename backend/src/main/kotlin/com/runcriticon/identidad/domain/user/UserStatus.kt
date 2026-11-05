package com.runcriticon.identidad.domain.user

/**
 * Estado del ciclo de vida de la cuenta. Solo una cuenta ACTIVO puede autenticarse. Los valores en mayúsculas son los
 * que se persisten en SQL (columna `estado`).
 *
 * [BAJA]: el admin decidió suprimir la cuenta y corren los 30 días de gracia (ADR-0014 D14). A diferencia de
 * [DESACTIVADO], termina en la supresión de la cuenta salvo que el admin la cancele.
 */
enum class UserStatus {
    INVITADO,
    ACTIVO,
    DESACTIVADO,
    BAJA,
    ;

    /**
     * Si una cuenta en este estado puede llegar a entrar al club: `ACTIVO`, o `INVITADO` porque aún puede activarse. Lo
     * usa la regla que impide dejar el club sin ningún administrador.
     */
    fun canEnter(): Boolean = this == ACTIVO || this == INVITADO
}
