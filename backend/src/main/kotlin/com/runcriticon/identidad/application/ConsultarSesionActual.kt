package com.runcriticon.identidad.application

import com.runcriticon.shared.autorizacion.ApplicationService
import com.runcriticon.shared.autorizacion.Principal
import com.runcriticon.shared.autorizacion.PrincipalProvider

/**
 * Devuelve el [Principal] de la sesión en curso (para `GET /api/sesion/actual`). Requiere sesión
 * activa, que garantiza la SecurityFilterChain; aquí solo se lee el principal del contexto.
 */
@ApplicationService
class ConsultarSesionActual(
    private val principalProvider: PrincipalProvider,
) {
    fun ejecutar(): Principal = principalProvider.actual()
}
