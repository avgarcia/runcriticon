package com.runcriticon

import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.autorizacion.spring.AuthScopeViolationException
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * Verifica que [com.runcriticon.shared.autorizacion.spring.AuthScopeEnforcementAspect] está
 * realmente tejido en el contexto de Spring (ADR-0009 D11): un `clubId` de argumento que no
 * coincide con el del principal de la sesión falla cerrado, y uno que coincide pasa. Y que cada
 * llamada a un método `@NoAuthScope` queda contada en la métrica de exenciones.
 */
class AuthScopeAspectIntegrationTest : IntegrationTestBase() {
    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var meterRegistry: MeterRegistry

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    private fun autenticarComo(clubId: UUID) {
        val principal = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ADMIN)
        val authentication =
            UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
    }

    @Test
    fun `clubId del argumento igual al del principal no lanza`() {
        val clubA = UUID.randomUUID()
        autenticarComo(clubA)

        userRepository.findById(ClubId.of(clubA), UserId.of(UUID.randomUUID()))
    }

    @Test
    fun `clubId del argumento distinto del principal falla cerrado`() {
        val clubA = UUID.randomUUID()
        val clubB = UUID.randomUUID()
        autenticarComo(clubA)

        assertThrows(AuthScopeViolationException::class.java) {
            userRepository.findById(ClubId.of(clubB), UserId.of(UUID.randomUUID()))
        }
    }

    @Test
    fun `cada llamada a un metodo @NoAuthScope cuenta en la metrica, con o sin principal`() {
        val email = Email.of("nadie-${UUID.randomUUID()}@runcriticon.local")
        val sinPrincipal = llamadasExentas("absent")
        val conPrincipal = llamadasExentas("present")

        // El login busca por email antes de que haya sesión: es la exención con la que se llama sin principal.
        userRepository.findByEmail(ClubId.of(UUID.randomUUID()), email)
        autenticarComo(UUID.randomUUID())
        userRepository.findByEmail(ClubId.of(UUID.randomUUID()), email)

        llamadasExentas("absent") shouldBe sinPrincipal + 1
        llamadasExentas("present") shouldBe conPrincipal + 1
    }

    private fun llamadasExentas(principal: String): Double =
        meterRegistry
            .find("shared.autorizacion.no_auth_scope.calls")
            .tags("module", "identidad", "method", "UserRepositoryImpl.findByEmail", "principal", principal)
            .counter()
            ?.count() ?: 0.0

    @Test
    fun `sin principal en el contexto falla cerrado`() {
        assertThrows(AuthScopeViolationException::class.java) {
            userRepository.findById(ClubId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()))
        }
    }
}
