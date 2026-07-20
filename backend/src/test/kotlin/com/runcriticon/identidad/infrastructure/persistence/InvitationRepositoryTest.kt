package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.InvitationEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.InvitationRepositoryImpl
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.matchers.nulls.shouldBeNull
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
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Test de integración del adaptador
 * [com.runcriticon.identidad.infrastructure.persistence.repositories.InvitationRepositoryImpl] (LAL-44): verifica que
 * la migración de Flyway aplica sobre Postgres real (Testcontainers) y que el round-trip guardar/recuperar/
 * consumir funciona correctamente.
 */
@SpringBootTest
@Testcontainers
class InvitationRepositoryTest {
    @Autowired
    private lateinit var invitationRepository: InvitationRepositoryImpl

    @Autowired
    private lateinit var invitationEntityRepository: InvitationEntityRepository

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
            // TokenHasherImpl exige el secreto no vacío al arrancar (fail-fast, ADR-0003 D13).
            registry.add("runcriticon.security.token-hmac-secret") { "test-hmac-secret-not-prod" }
            registry.add("runcriticon.observability.userid-hash-salt") { "test-userid-hash-salt-not-prod" }
        }
    }

    private val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val now: Instant = Instant.parse("2026-06-19T10:00:00Z")
    private val tokenHash = TokenHash("hash-test-abc123efgh456ij")

    private var userId: UserId = UserId.of(UUID.randomUUID())

    @BeforeEach
    fun crearUsuario() {
        val entity =
            UserEntity(
                id = UUID.randomUUID(),
                clubId = clubId.value,
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
        invitationEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
    }

    @Test
    fun `save y findByTokenHash devuelven la invitación emitida`() {
        val invitation = Invitation.issue(userId, clubId, tokenHash, now)
        invitationRepository.save(invitation)

        val found = invitationRepository.findByTokenHash(tokenHash)

        found.shouldNotBeNull()
        found.userId shouldBe userId
        found.clubId shouldBe clubId
        found.consumedAt.shouldBeNull()
    }

    @Test
    fun `round-trip consume - consumedAt persiste tras save`() {
        val invitation = Invitation.issue(userId, clubId, tokenHash, now)
        invitationRepository.save(invitation)

        val loaded = invitationRepository.findByTokenHash(tokenHash)!!
        val consumedAt = now.plus(Duration.ofMinutes(1))
        val consumed = loaded.consume(tokenHash, consumedAt).getOrNull()!!
        invitationRepository.save(consumed)

        val reloaded = invitationRepository.findByTokenHash(tokenHash)!!
        reloaded.consumedAt shouldBe consumedAt
    }

    @Test
    fun `findLatestByUserId devuelve la invitación más reciente del usuario`() {
        val otherHash = TokenHash("hash-test-otro-xyz789uvw012")
        val first = Invitation.issue(userId, clubId, tokenHash, now)
        val second = Invitation.issue(userId, clubId, otherHash, now.plus(Duration.ofMinutes(5)))
        invitationRepository.save(first)
        invitationRepository.save(second)

        val latest = invitationRepository.findLatestByUserId(userId)

        latest.shouldNotBeNull()
        latest.tokenHash shouldBe otherHash
    }

    @Test
    fun `findByTokenHash con hash inexistente devuelve null`() {
        invitationRepository.findByTokenHash(TokenHash("no-existe")).shouldBeNull()
    }
}
