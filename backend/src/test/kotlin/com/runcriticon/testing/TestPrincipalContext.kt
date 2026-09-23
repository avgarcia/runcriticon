package com.runcriticon.testing

import com.runcriticon.shared.autorizacion.model.Principal
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Autentica [Principal] en el `SecurityContext` de la JVM de test, con el mismo token que construía a mano
 * cada test de integración (`autenticar(principal)`, ~30 apariciones). Solo hace falta para los tests que
 * pasan por [com.runcriticon.shared.autorizacion.spring.AuthScopeEnforcementAspect] — los casos de uso reciben
 * el `actor` como parámetro explícito, no lo leen del `SecurityContext`.
 */
object TestPrincipalContext {
    fun set(principal: Principal) {
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        SecurityContextHolder.setContext(context)
    }

    fun clear() {
        SecurityContextHolder.clearContext()
    }

    /** Autentica [principal] solo durante [block]; limpia el contexto incluso si [block] lanza. */
    fun <T> withPrincipal(
        principal: Principal,
        block: () -> T,
    ): T {
        set(principal)
        try {
            return block()
        } finally {
            clear()
        }
    }
}
