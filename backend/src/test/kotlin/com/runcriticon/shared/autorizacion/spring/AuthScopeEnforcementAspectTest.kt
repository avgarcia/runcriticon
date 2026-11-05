package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import java.util.UUID

private interface FakeScopedRepository {
    fun findScoped(clubId: UUID): String

    fun findWithoutScope(clubId: UUID): String

    fun findWithoutClubId(otherId: UUID): String

    fun findExempt(clubId: ClubId): String
}

private class FakeScopedRepositoryImpl : FakeScopedRepository {
    @AuthScope(Scope.CLUB)
    override fun findScoped(clubId: UUID): String = "ok:$clubId"

    @AuthScope
    override fun findWithoutScope(clubId: UUID): String = "ok:$clubId"

    @AuthScope(Scope.CLUB)
    override fun findWithoutClubId(otherId: UUID): String = "ok:$otherId"

    // Con un parámetro value class, como los repositorios reales: Kotlin le añade un sufijo al nombre JVM.
    @NoAuthScope("exención de prueba")
    override fun findExempt(clubId: ClubId): String = "exento"
}

/** Unitario del aspecto verificador de [AuthScope] (ADR-0009 D11): sin contexto Spring, con proxy directo. */
class AuthScopeEnforcementAspectTest :
    FunSpec({
        val clubA = UUID.randomUUID()
        val clubB = UUID.randomUUID()
        val principalProvider = mockk<PrincipalProvider>()
        val meterRegistry = SimpleMeterRegistry()
        val aspect = AuthScopeEnforcementAspect(principalProvider, meterRegistry)

        fun proxied(): FakeScopedRepository {
            val factory = AspectJProxyFactory(FakeScopedRepositoryImpl())
            factory.addAspect(aspect)
            return factory.getProxy(javaClass.classLoader) as FakeScopedRepository
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

        test("@AuthScope sin ningun scope: falla cerrado") {
            every { principalProvider.current() } returns Principal(UUID.randomUUID(), clubA, Role.ADMIN)
            shouldThrow<AuthScopeViolationException> { proxied().findWithoutScope(clubA) }
        }

        fun exemptCalls(principal: String): Double =
            meterRegistry
                .find("shared.autorizacion.no_auth_scope.calls")
                .tags("module", "shared", "method", "FakeScopedRepositoryImpl.findExempt", "principal", principal)
                .counter()
                ?.count() ?: 0.0

        test("un metodo @NoAuthScope pasa y cuenta la llamada como sin principal cuando no hay sesion") {
            every { principalProvider.currentOrNull() } returns null
            val before = exemptCalls("absent")

            proxied().findExempt(ClubId.of(clubA)) shouldBe "exento"

            exemptCalls("absent") shouldBe before + 1
        }

        test("un metodo @NoAuthScope llamado con sesion cuenta la llamada como con principal") {
            every { principalProvider.currentOrNull() } returns Principal(UUID.randomUUID(), clubA, Role.ADMIN)
            val before = exemptCalls("present")

            proxied().findExempt(ClubId.of(clubA)) shouldBe "exento"

            exemptCalls("present") shouldBe before + 1
        }
    })
