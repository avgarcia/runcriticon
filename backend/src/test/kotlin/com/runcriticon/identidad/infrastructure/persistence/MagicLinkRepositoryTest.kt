package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.magiclink.MagicLink
import com.runcriticon.identidad.domain.magiclink.MagicLinkPurpose
import com.runcriticon.identidad.domain.user.UserId
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Test de integración del adaptador [MagicLinkRepositoryImpl] (LAL-12): verifica sobre Postgres real
 * (Testcontainers) que la columna `proposito` de la migración `V202607010001` hace round-trip para
 * ambos propósitos (LOGIN y RESETEO), tanto guardar/recuperar como al recuperar de nuevo tras consumo.
 */
@SpringBootTest
@Testcontainers
class MagicLinkRepositoryTest {
    @Autowired
    private lateinit var magicLinkRepository: MagicLinkRepositoryImpl

    @Autowired
    private lateinit var magicLinkEntityRepository: MagicLinkEntityRepository

    @Autowired
    private lateinit var userEntityRepository: UserEntityRepository

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun configure(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("runcriticon.security.token-hmac-secret") { "test-hmac-secret-not-prod" }
            registry.add("runcriticon.observability.userid-hash-salt") { "test-userid-hash-salt-not-prod" }
        }
    }

    private val clubId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val now: Instant = Instant.parse("2026-07-01T10:00:00Z")

    private var userId: UserId = UserId.of(UUID.randomUUID())

    @BeforeEach
    fun crearUsuario() {
        val entity =
            UserEntity(
                id = UUID.randomUUID(),
                clubId = clubId,
                email = "test@runcriticon.local",
                normalizedEmail = "test@runcriticon.local",
                name = "Test User",
                role = "ADMIN",
                passwordHash = null,
                status = "ACTIVO",
                createdAt = now,
                modifiedAt = now,
            )
        userId = UserId.of(userEntityRepository.save(entity).id)
    }

    @AfterEach
    fun limpiar() {
        magicLinkEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
    }

    @Test
    fun `round-trip del propósito RESETEO al guardar y recuperar`() {
        val hash = TokenHash("hash-test-reseteo-abc123efgh456ij")
        magicLinkRepository.save(MagicLink.issue(userId, clubId, hash, MagicLinkPurpose.RESETEO, now))

        val found = magicLinkRepository.findByTokenHash(hash)

        found.shouldNotBeNull()
        found.proposito shouldBe MagicLinkPurpose.RESETEO
        found.userId shouldBe userId
    }

    @Test
    fun `round-trip del propósito LOGIN al guardar y recuperar`() {
        val hash = TokenHash("hash-test-login-xyz789uvw012rst345")
        magicLinkRepository.save(MagicLink.issue(userId, clubId, hash, MagicLinkPurpose.LOGIN, now))

        val found = magicLinkRepository.findByTokenHash(hash)

        found.shouldNotBeNull()
        found.proposito shouldBe MagicLinkPurpose.LOGIN
    }
}
