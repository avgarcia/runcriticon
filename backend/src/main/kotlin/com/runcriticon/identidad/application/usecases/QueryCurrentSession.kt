package com.runcriticon.identidad.application.usecases

import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.AuthenticatedOnly
import com.runcriticon.shared.autorizacion.model.Principal

/**
 * Devuelve el [Principal] de la sesión en curso (para `GET /api/sesion/actual`). Requiere sesión
 * activa, que garantiza la SecurityFilterChain; aquí solo se lee el principal del contexto: no hay
 * regla de la [com.runcriticon.shared.autorizacion.AuthorizationMatrix] que aplicar (LAL-37,
 * ADR-0009 D13 opción d).
 */
@ApplicationService
@AuthenticatedOnly("Solo devuelve el principal de la sesión en curso; no hay recurso que autorizar (LAL-37)")
class QueryCurrentSession(
    private val principalProvider: PrincipalProvider,
) {
    fun execute(): Principal = principalProvider.current()
}
