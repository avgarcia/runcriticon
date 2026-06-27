package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
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
 * Test de integración del adaptador [UserRepositoryImpl] sobre Postgres real (Testcontainers).
 * Regresión: un re-save de un usuario ya existente (p. ej. activación de cuenta o cambio de
 * contraseña) NO debe pisar la fecha de creación `creado_en` con el `now` del merge JPA.
 */
@SpringBootTest
@Testcontainers
class UserRepositoryTest {
    @Autowired
    private lateinit var userRepository: UserRepositoryImpl

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
        }
    }

    private val clubId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val originalCreatedAt: Instant = Instant.parse("2026-01-01T08:00:00Z")

    @AfterEach
    fun limpiar() {
        userEntityRepository.deleteAll()
    }

    @Test
    fun `un segundo save de un usuario existente preserva creado_en`() {
        // Alta: fila con una fecha de creación conocida en el pasado.
        val id = UUID.randomUUID()
        userEntityRepository.save(
            UserEntity(
                id = id,
                clubId = clubId,
                email = "alumno@runcriticon.local",
                normalizedEmail = "alumno@runcriticon.local",
                name = "Alumno Test",
                role = Role.ALUMNO.name,
                passwordHash = null,
                status = UserStatus.INVITADO.name,
                createdAt = originalCreatedAt,
                modifiedAt = originalCreatedAt,
            ),
        )

        // Activación: el agregado de dominio (sin createdAt) se re-guarda con contraseña y estado ACTIVO.
        val activated =
            User(
                id = UserId.of(id),
                clubId = clubId,
                email = Email.of("alumno@runcriticon.local"),
                name = "Alumno Test",
                role = Role.ALUMNO,
                passwordHash = "argon2id-hash",
                status = UserStatus.ACTIVO,
            )
        userRepository.save(activated)

        val reloaded = userEntityRepository.findById(id).orElseThrow()
        // El bug: creado_en se sobrescribía con el now del merge. Debe seguir siendo el original.
        reloaded.createdAt shouldBe originalCreatedAt
        // El update sí persiste y modificado_en avanza.
        reloaded.status shouldBe UserStatus.ACTIVO.name
        reloaded.passwordHash shouldBe "argon2id-hash"
        reloaded.modifiedAt shouldBeGreaterThan originalCreatedAt
    }
}
