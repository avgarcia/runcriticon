package com.runcriticon.shared.autorizacion

import com.runcriticon.shared.autorizacion.model.Principal

/**
 * Abstrae de dónde sale el [Principal] de la petición en curso. Lo implementa la capa de infraestructura del núcleo a
 * partir del `SecurityContext` de Spring Security; el `SecurityContextHolder` solo puede tocarse aquí
 * (lo verifica `AuthorizationArchTest`).
 */
interface PrincipalProvider {
    /** El usuario autenticado de la petición actual. */
    fun current(): Principal
}
