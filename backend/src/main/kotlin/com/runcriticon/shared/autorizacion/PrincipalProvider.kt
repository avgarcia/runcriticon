package com.runcriticon.shared.autorizacion

import com.runcriticon.shared.autorizacion.modelo.Principal

/**
 * Abstrae de dónde sale el [Principal] de la petición en curso (ADR-0009 D6). Lo implementa
 * la capa de infraestructura del núcleo a partir del `SecurityContext` de Spring Security; el
 * `SecurityContextHolder` solo puede tocarse aquí (lo verifica `AutorizacionArchTest`).
 *
 * El cuerpo se difiere a Bloque 5 (login mínimo). En H0 es solo el contrato.
 */
interface PrincipalProvider {
    /** El usuario autenticado de la petición actual. */
    fun current(): Principal
}
