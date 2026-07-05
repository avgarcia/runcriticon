package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import java.util.UUID

private interface FakeScopedRepository {
    fun findScoped(clubId: UUID): String

    fun findOwned(clubId: UUID): String

    fun findWithoutClubId(otherId: UUID): String
}

private class FakeScopedRepositoryImpl : FakeScopedRepository {
    @AuthScope(Scope.CLUB)
    override fun findScoped(clubId: UUID): String = "ok:$clubId"

    @AuthScope(Scope.OWNED)
    override fun findOwned(clubId: UUID): String = "ok:$clubId"

    @AuthScope(Scope.CLUB)
    override fun findWithoutClubId(otherId: UUID): String = "ok:$otherId"
}

/** Unitario del aspecto verificador de [AuthScope] (ADR-0009 D11): sin contexto Spring, con proxy directo. */
class AuthScopeEnforcementAspectTest :
    FunSpec({
        val clubA = UUID.randomUUID()
        val clubB = UUID.randomUUID()
        val principalProvider = mockk<PrincipalProvider>()
        val aspect = AuthScopeEnforcementAspect(principalProvider)

        fun proxied(): FakeScopedRepository {
            val factory = AspectJProxyFactory(FakeScopedRepositoryImpl())
            factory.addAspect(aspect)
            return factory.proxy as FakeScopedRepository
        }

        test("clubId del argumento coincide con el del principal: pasa") {
            every { principalProvider.current() } returns Principal(UUID.randomUUID(), clubA, Role.ADMIN)
            proxied().findScoped(clubA) shouldBe "ok:$clubA"
        }

        test("clubId del argumento no coincide con el del principal: falla cerrado") {
            every { principalProvider.current() } returns Principal(UUID.randomUUID(), clubA, Role.ADMIN)
            shouldThrow<AuthScopeViolationException> { proxied().findScoped(clubB) }
        }

        test("sin principal en el contexto: falla cerrado") {
            every { principalProvider.current() } throws IllegalStateException("sin sesión")
            shouldThrow<AuthScopeViolationException> { proxied().findScoped(clubA) }
        }

        test("metodo @AuthScope(CLUB) sin parametro clubId: falla cerrado") {
            every { principalProvider.current() } returns Principal(UUID.randomUUID(), clubA, Role.ADMIN)
            shouldThrow<AuthScopeViolationException> { proxied().findWithoutClubId(clubA) }
        }

        test("scope sin verificacion implementada (OWNED): falla cerrado") {
            every { principalProvider.current() } returns Principal(UUID.randomUUID(), clubA, Role.ADMIN)
            shouldThrow<AuthScopeViolationException> { proxied().findOwned(clubA) }
        }
    })
