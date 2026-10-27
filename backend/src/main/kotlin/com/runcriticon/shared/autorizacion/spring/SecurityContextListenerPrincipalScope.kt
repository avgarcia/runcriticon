package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.ListenerPrincipalScope
import com.runcriticon.shared.autorizacion.model.Principal
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * [ListenerPrincipalScope] sobre el `SecurityContextHolder` de Spring Security: el mismo sitio del que lee
 * [SpringSecurityPrincipalProvider], así que el aspecto de `@AuthScope` ve el principal sin saber que viene de un
 * evento.
 *
 * **Restaura el contexto anterior en `finally`**, no se limita a limpiarlo: los hilos de los listeners son de un pool,
 * y un contexto que se quedara puesto lo heredaría el siguiente evento que cayera en ese hilo. Si no había
 * autenticación previa, deja el hilo sin contexto.
 */
@Component
class SecurityContextListenerPrincipalScope : ListenerPrincipalScope {
    override fun <T> runAs(
        principal: Principal,
        block: () -> T,
    ): T {
        val previous = SecurityContextHolder.getContext()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        SecurityContextHolder.setContext(context)
        try {
            return block()
        } finally {
            if (previous.authentication == null) {
                SecurityContextHolder.clearContext()
            } else {
                SecurityContextHolder.setContext(previous)
            }
        }
    }
}
