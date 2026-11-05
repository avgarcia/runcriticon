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

    /**
     * Como [current], pero devuelve `null` si no hay principal en curso en vez de fallar. Es para quien solo necesita
     * saber si lo hay en un camino caliente, sin pagar una excepción en cada llamada sin sesión.
     */
    fun currentOrNull(): Principal? = runCatching { current() }.getOrNull()
}
