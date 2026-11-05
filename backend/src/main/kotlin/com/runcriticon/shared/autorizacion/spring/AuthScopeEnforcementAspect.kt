package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import io.micrometer.core.instrument.MeterRegistry
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import java.util.UUID

/**
 * Verifica en runtime la firma `@AuthScope(Scope.CLUB)` de los métodos de `@Repository`. **No inyecta el filtro** — el
 * propio método ya recibe `clubId` y lo aplica en su query (D4); este aspecto es la red de seguridad que falla cerrado
 * si esa firma se rompe o se invoca con datos de otro club.
 *
 * `CLUB` es el único scope (ADR-0009 D11). Un `@AuthScope()` sin él también falla cerrado: un `@AuthScope` declarado
 * nunca queda sin nadie que lo haga cumplir.
 *
 * Los métodos `@NoAuthScope` no se verifican, pero sí se cuentan ([recordExemption]): es la señal de revisión de
 * ADR-0009 D11 sobre las exenciones.
 *
 * Riesgo conocido: solo actúa sobre llamadas que pasan por el proxy Spring (bean del repositorio); una auto-invocación
 * interna del mismo bean lo esquiva. La anotación debe seguir en la clase que implementa el método (el impl del
 * `@Repository`), no en el puerto — Spring AOP no hereda anotaciones de método declaradas en la interfaz.
 */
@Aspect
@Component
class AuthScopeEnforcementAspect(
    private val principalProvider: PrincipalProvider,
    private val meterRegistry: MeterRegistry,
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
        if (Scope.CLUB !in authScope.scopes) fail("@AuthScope sin Scope.CLUB en ${joinPoint.signature}")

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

    /**
     * Cuenta cada llamada a un método `@NoAuthScope` (ADR-0009 D11) y deja su justificación en un log `DEBUG`. La
     * métrica muestra qué exenciones se usan, cuánto, y si alguna justificada como «sin principal» se llama desde una
     * petición con usuario (`principal=present`).
     *
     * No es un log `INFO`: varias exenciones están en caminos que se ejecutan constantemente (la idempotencia de cada
     * entrega de evento, la escritura de auditoría, el gauge del outbox). Por la misma razón comprueba el principal con
     * [PrincipalProvider.currentOrNull], que no lanza excepción cuando no hay sesión.
     *
     * Cardinalidad acotada: un valor de `method` por método exento, por dos de `principal`.
     */
    @Before("@annotation(noAuthScope)")
    fun recordExemption(
        joinPoint: JoinPoint,
        noAuthScope: NoAuthScope,
    ) {
        // La clase del bean real, no `signature.declaringType`: si la llamada llega por el puerto, este es la interfaz,
        // y la anotación vive en la implementación. `getUserClass` quita el sufijo de una subclase CGLIB.
        val declaringType = ClassUtils.getUserClass(joinPoint.target ?: joinPoint.signature.declaringType)
        // Kotlin añade un sufijo `-<hash>` al nombre JVM de las funciones con parámetros value class (`ClubId`,
        // `Email`): se quita para que el tag sea el nombre del código y no cambie cada vez que cambia la firma.
        val method = "${declaringType.simpleName}.${joinPoint.signature.name.substringBefore('-')}"
        val principal = if (principalProvider.currentOrNull() != null) PRINCIPAL_PRESENT else PRINCIPAL_ABSENT
        meterRegistry
            .counter(EXEMPT_CALLS, "module", moduleOf(declaringType), "method", method, "principal", principal)
            .increment()
        log.debug("@NoAuthScope {} (principal {}): {}", method, principal, noAuthScope.justificacion)
    }

    private fun fail(message: String): Nothing = throw AuthScopeViolationException(message)

    private companion object {
        val log = LoggerFactory.getLogger(AuthScopeEnforcementAspect::class.java)

        const val EXEMPT_CALLS = "shared.autorizacion.no_auth_scope.calls"
        const val PRINCIPAL_PRESENT = "present"
        const val PRINCIPAL_ABSENT = "absent"
        const val ROOT_PACKAGE = "com.runcriticon."

        /** El módulo de Spring Modulith del método: el paquete que cuelga de `com.runcriticon` (`shared` incluido). */
        fun moduleOf(type: Class<*>): String = type.name.removePrefix(ROOT_PACKAGE).substringBefore('.')
    }
}
