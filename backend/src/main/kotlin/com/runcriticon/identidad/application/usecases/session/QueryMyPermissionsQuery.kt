package com.runcriticon.identidad.application.usecases.session

import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.AuthenticatedOnly
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource

/**
 * `GET /me/permissions`: devuelve las acciones que la [AuthorizationMatrix] concede al rol del principal actual,
 * agrupadas por recurso. Es ayuda de UX para ocultar botones en el frontend — nunca una barrera; cada petición real se
 * sigue autorizando en el servidor.
 */
@ApplicationService
@AuthenticatedOnly(
    "Devuelve los propios permisos del rol autenticado; no hay recurso de terceros que autorizar",
)
class QueryMyPermissionsQuery(
    private val principalProvider: PrincipalProvider,
) {
    fun execute(): Map<Resource, Set<Action>> = AuthorizationMatrix.grantedTo(principalProvider.current().role)
}
