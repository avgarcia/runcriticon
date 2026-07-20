package com.runcriticon

import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.autorizacion.spring.AuthScopeViolationException
import com.runcriticon.shared.tenancy.ClubId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Verifica que [com.runcriticon.shared.autorizacion.spring.AuthScopeEnforcementAspect] está
 * realmente tejido en el contexto de Spring (ADR-0009 D11, LAL-59): un `clubId` de argumento que no
 * coincide con el del principal de la sesión falla cerrado, y uno que coincide pasa.
 */
@SpringBootTest
@Testcontainers
class AuthScopeAspectIntegrationTest {
    @Autowired
    lateinit var userRepository: UserRepository

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
    fun `sin principal en el contexto falla cerrado`() {
        assertThrows(AuthScopeViolationException::class.java) {
            userRepository.findById(ClubId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()))
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun propiedades(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("runcriticon.security.token-hmac-secret") { "test-hmac-secret-not-prod" }
            registry.add("runcriticon.observability.userid-hash-salt") { "test-userid-hash-salt-not-prod" }
        }
    }
}
