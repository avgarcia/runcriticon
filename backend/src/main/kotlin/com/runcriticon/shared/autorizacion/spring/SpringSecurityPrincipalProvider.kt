package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.model.Principal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Implementación de [PrincipalProvider] sobre el `SecurityContext` de Spring Security (ADR-0009 D6).
 * Vive en `shared.autorizacion` porque es el **único** sitio donde se permite tocar
 * `SecurityContextHolder` (lo verifica `AuthorizationArchTest`).
 */
@Component
class SpringSecurityPrincipalProvider : PrincipalProvider {
    override fun current(): Principal {
        val authentication =
            SecurityContextHolder.getContext().authentication
                ?: error("No hay autenticación en el contexto de seguridad")
        val principal = authentication.principal
        require(principal is Principal) {
            "El principal del contexto no es un Principal de Runcriticon: ${principal?.javaClass}"
        }
        return principal
    }
}
