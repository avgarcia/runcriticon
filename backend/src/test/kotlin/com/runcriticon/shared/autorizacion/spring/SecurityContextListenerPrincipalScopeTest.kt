package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * [SecurityContextListenerPrincipalScope]: el principal está en curso durante el bloque —lo lee
 * [SpringSecurityPrincipalProvider], igual que el aspecto de `@AuthScope`— y después el hilo vuelve a como estaba,
 * también si el bloque lanza. Un hilo de listener es de pool: un contexto olvidado lo heredaría el siguiente evento.
 */
class SecurityContextListenerPrincipalScopeTest :
    FunSpec({
        val scope = SecurityContextListenerPrincipalScope()
        val provider = SpringSecurityPrincipalProvider()
        val alumno = Principal(userId = UUID.randomUUID(), clubId = UUID.randomUUID(), role = Role.ALUMNO)

        afterEach { SecurityContextHolder.clearContext() }

        test("durante el bloque el principal en curso es el pedido") {
            scope.runAs(alumno) { provider.current() } shouldBe alumno
        }

        test("sin contexto previo, el hilo queda sin autenticacion al terminar") {
            scope.runAs(alumno) { }

            SecurityContextHolder.getContext().authentication.shouldBeNull()
        }

        test("con contexto previo, se restaura el mismo") {
            val previo = SecurityContextHolder.createEmptyContext()
            previo.authentication = UsernamePasswordAuthenticationToken("otro", null, emptyList())
            SecurityContextHolder.setContext(previo)

            scope.runAs(alumno) { }

            SecurityContextHolder.getContext() shouldBeSameInstanceAs previo
        }

        test("si el bloque lanza, el contexto tambien se restaura") {
            shouldThrow<IllegalStateException> { scope.runAs(alumno) { error("falla el listener") } }

            SecurityContextHolder.getContext().authentication.shouldBeNull()
        }
    })
