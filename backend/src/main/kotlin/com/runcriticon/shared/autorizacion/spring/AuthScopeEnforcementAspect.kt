package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Verifica en runtime la firma `@AuthScope(Scope.CLUB)` de los métodos de `@Repository`. **No inyecta el filtro** — el
 * propio método ya recibe `clubId` y lo aplica en su query (D4); este aspecto es la red de seguridad que falla cerrado
 * si esa firma se rompe o se invoca con datos de otro club.
 *
 * Scopes distintos de `CLUB` (`OWNED`, `GRUPOS_DEL_ENTRENADOR`, `MIS_GRUPOS`) no tienen todavía una verificación
 * equivalente implementada: el aspecto falla cerrado en cuanto los detecta, para que un `@AuthScope` declarado nunca
 * quede sin nadie que lo haga cumplir.
 *
 * Riesgo conocido: solo actúa sobre llamadas que pasan por el proxy Spring (bean del repositorio); una auto-invocación
 * interna del mismo bean lo esquiva. La anotación debe seguir en la clase que implementa el método (el impl del
 * `@Repository`), no en el puerto — Spring AOP no hereda anotaciones de método declaradas en la interfaz.
 */
@Aspect
@Component
class AuthScopeEnforcementAspect(
    private val principalProvider: PrincipalProvider,
) {
    /**
     * Verifica en runtime la firma `@AuthScope(Scope.CLUB)` de los métodos de `@Repository`.
     * Riesgo conocido: solo actúa sobre llamadas que pasan por el proxy Spring (bean del repositorio); una
     * auto-invocación interna del mismo bean lo esquiva. La anotación debe seguir en la clase que implementa el método
     * (el impl del `@Repository`), no en el puerto — Spring AOP no hereda anotaciones de método declaradas en la
     * interfaz.
     */
    @Before("@annotation(authScope)")
    fun enforce(
        joinPoint: JoinPoint,
        authScope: AuthScope,
    ) {
        val unsupported = authScope.scopes.firstOrNull { it != Scope.CLUB }
        if (unsupported != null) {
            fail("Scope $unsupported declarado en ${joinPoint.signature} sin verificación implementada por el aspecto")
        }
        if (Scope.CLUB !in authScope.scopes) return

        val principal =
            runCatching { principalProvider.current() }
                .getOrElse { fail("@AuthScope(CLUB) invocado sin principal en ${joinPoint.signature}") }

        val signature = joinPoint.signature as MethodSignature
        val index = signature.parameterNames?.indexOf("clubId") ?: -1
        if (index < 0) fail("@AuthScope(CLUB) sin parámetro clubId en ${joinPoint.signature}")

        val clubId =
            joinPoint.args.getOrNull(index) as? UUID
                ?: fail("clubId nulo o de tipo inesperado en ${joinPoint.signature}")
        if (clubId != principal.clubId) {
            fail("clubId $clubId no coincide con el club del principal en ${joinPoint.signature}")
        }
    }

    private fun fail(message: String): Nothing = throw AuthScopeViolationException(message)
}
