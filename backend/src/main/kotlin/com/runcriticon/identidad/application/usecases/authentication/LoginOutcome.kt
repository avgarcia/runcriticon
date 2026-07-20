package com.runcriticon.identidad.application.usecases.authentication

import com.runcriticon.shared.autorizacion.model.Principal

/**
 * Resultado del login con contraseña. Separa el caso normal del de contraseña caducada: con la contraseña caducada NO
 * se concede acceso (no se crea sesión), pero tampoco es un error de credenciales — el usuario debe fijar una nueva
 * antes de entrar.
 */
sealed interface LoginOutcome {
    /** Credenciales válidas y contraseña vigente: la capa api crea la sesión con el [principal]. */
    data class Authenticated(
        val principal: Principal,
    ) : LoginOutcome

    /** Credenciales válidas pero contraseña caducada (>90 días): exige cambio antes de entrar. */
    data object PasswordExpired : LoginOutcome
}
