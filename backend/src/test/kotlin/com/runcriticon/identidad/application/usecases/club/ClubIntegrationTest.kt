package com.runcriticon.identidad.application.usecases.club

import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.ClubEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Tests de integración de la migración `V202607210001__crea_club` y del flujo GET/PATCH del club
 * sobre Postgres real (Testcontainers): la migración aplica, la fila semilla del club
 * canónico existe, la FK `usuario.club_id → club.id` está creada y funciona, y `UpdateClubCommand`
 * persiste el nombre nuevo.
 */
@SpringBootTest
@Testcontainers
class ClubIntegrationTest {
    @Autowired private lateinit var queryClub: QueryClubQuery

    @Autowired private lateinit var updateClub: UpdateClubCommand

    @Autowired private lateinit var clubEntityRepository: ClubEntityRepository

    @Autowired private lateinit var userEntityRepository: UserEntityRepository

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
            registry.add("runcriticon.security.token-hmac-secret") { "test-token-hmac-secret-0123456789" }
            registry.add("runcriticon.observability.userid-hash-salt") { "test-userid-hash-salt-not-prod" }
        }
    }

    private val canonicalClubId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val admin = Principal(userId = UUID.randomUUID(), clubId = canonicalClubId, role = Role.ADMIN)

    @BeforeEach
    fun autenticar() {
        // AuthScopeEnforcementAspect verifica el clubId de @AuthScope(CLUB) contra el
        // principal de SecurityContextHolder; este test invoca los casos de uso directamente (sin
        // pasar por login HTTP), así que hay que sembrar el contexto igual que haría
        // SecuritySessionManager.
        val authentication =
            UsernamePasswordAuthenticationToken(admin, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        // Tests independientes del orden de ejecución: restaura el nombre sembrado por la migración
        // por si un test anterior lo cambió (misma instancia de Postgres para toda la clase).
        val canonical = clubEntityRepository.findById(canonicalClubId).orElseThrow()
        canonical.name = "Mi club"
        clubEntityRepository.save(canonical)
    }

    @AfterEach
    fun limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `la migracion siembra la fila del club canonico`() {
        val club = clubEntityRepository.findById(canonicalClubId).orElseThrow()
        club.name shouldBe "Mi club"
    }

    @Test
    fun `GET club devuelve la ficha sembrada por la migracion`() {
        val result = queryClub.execute(admin).shouldBeRight()
        result.id.value shouldBe canonicalClubId
    }

    @Test
    fun `PATCH club persiste el nombre nuevo`() {
        updateClub.execute(admin, "Club Runcriticon").shouldBeRight()

        val reloaded = clubEntityRepository.findById(canonicalClubId).orElseThrow()
        reloaded.name shouldBe "Club Runcriticon"
    }

    @Test
    fun `la FK usuario_club_fk rechaza un club_id sin fila de club`() {
        val orphanClubId = UUID.randomUUID()
        shouldThrow<DataIntegrityViolationException> {
            userEntityRepository.saveAndFlush(
                UserEntity(
                    id = UUID.randomUUID(),
                    clubId = orphanClubId,
                    email = "huerfano@runcriticon.local",
                    normalizedEmail = "huerfano@runcriticon.local",
                    name = "Sin Club",
                    role = Role.ALUMNO.name,
                    passwordHash = null,
                    status = "INVITADO",
                    createdAt = Instant.now(),
                    modifiedAt = Instant.now(),
                ),
            )
        }
    }

    @Test
    fun `PATCH sobre un club inexistente devuelve NotFound`() {
        val otherClubId = UUID.randomUUID()
        val otherAdmin = Principal(userId = UUID.randomUUID(), clubId = otherClubId, role = Role.ADMIN)
        val authentication =
            UsernamePasswordAuthenticationToken(otherAdmin, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        updateClub.execute(otherAdmin, "Otro nombre").shouldBeLeft(IdentidadError.NotFound)
    }
}
