package com.runcriticon.identidad.application.usecases

import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.anotaciones.ApplicationService
import com.runcriticon.shared.autorizacion.modelo.Principal

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
