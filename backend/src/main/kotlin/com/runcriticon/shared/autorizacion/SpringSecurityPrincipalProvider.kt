package com.runcriticon.shared.autorizacion

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Implementación de [PrincipalProvider] sobre el `SecurityContext` de Spring Security (ADR-0009 D6).
 * Vive en `shared.autorizacion` porque es el **único** sitio donde se permite tocar
 * `SecurityContextHolder` (lo verifica `AutorizacionArchTest`).
 */
@Component
class SpringSecurityPrincipalProvider : PrincipalProvider {
    override fun actual(): Principal {
        val autenticacion =
            SecurityContextHolder.getContext().authentication
                ?: error("No hay autenticación en el contexto de seguridad")
        val principal = autenticacion.principal
        require(principal is Principal) {
            "El principal del contexto no es un Principal de Runcriticon: ${principal?.javaClass}"
        }
        return principal
    }
}
