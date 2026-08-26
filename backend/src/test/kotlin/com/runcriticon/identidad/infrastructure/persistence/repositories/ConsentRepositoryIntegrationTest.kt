package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.consent.ConsentText
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * [ConsentRepositoryImpl] contra Postgres real: `save` resuelve insert-o-update por id (Hibernate
 * `merge`, sin `@GeneratedValue` ni `Persistable`), el filtro por club de `@AuthScope(Scope.CLUB)`, y
 * que revocar solo rellena `revocado_en` sin tocar `concedido_en` de la fila original.
 */
class ConsentRepositoryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var repository: ConsentRepositoryImpl

    @Autowired private lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `sin ninguna fila, findLatestByUserId devuelve null`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val userId = UserId.of(UUID.randomUUID())
        autenticar(clubId, userId)

        repository.findLatestByUserId(clubId, userId).shouldBeNull()
    }

    @Test
    fun `save inserta una fila nueva, recuperable por findLatestByUserId`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val userId = UserId.of(UUID.randomUUID())
        autenticar(clubId, userId)
        val consent = grant(userId, clubId)

        repository.save(consent)

        val latest = repository.findLatestByUserId(clubId, userId)
        latest?.id shouldBe consent.id
        latest?.isActive() shouldBe true
    }

    @Test
    fun `revocar la misma fila actualiza revocado_en sin tocar concedido_en`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val userId = UserId.of(UUID.randomUUID())
        autenticar(clubId, userId)
        val consent = grant(userId, clubId)
        repository.save(consent)

        val revoked = consent.revoke(Instant.parse("2026-08-25T12:00:00Z"))
        repository.save(revoked)

        val filas = jdbc.queryForObject(COUNT_SQL, Int::class.java, userId.value)
        filas shouldBe 1
        val latest = repository.findLatestByUserId(clubId, userId)
        latest?.grantedAt shouldBe consent.grantedAt
        latest?.revokedAt shouldBe revoked.revokedAt
        latest?.isActive() shouldBe false
    }

    @Test
    fun `conceder de nuevo tras revocar crea una fila distinta, sin colisionar por version de texto`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val userId = UserId.of(UUID.randomUUID())
        autenticar(clubId, userId)
        val first = grant(userId, clubId)
        repository.save(first)
        repository.save(first.revoke(Instant.now()))

        val second = grant(userId, clubId)
        repository.save(second)

        val filas = jdbc.queryForObject(COUNT_SQL, Int::class.java, userId.value)
        filas shouldBe 2
        val latest = repository.findLatestByUserId(clubId, userId)
        latest?.id shouldBe second.id
        latest?.isActive() shouldBe true
    }

    @Test
    fun `findLatestByUserId no trae filas de otro club ni de otro usuario`() {
        val clubId = ClubId.of(UUID.randomUUID())
        val userId = UserId.of(UUID.randomUUID())
        autenticar(clubId, userId)
        repository.save(grant(UserId.of(UUID.randomUUID()), clubId))
        repository.save(grant(userId, ClubId.of(UUID.randomUUID())))

        repository.findLatestByUserId(clubId, userId).shouldBeNull()
    }

    @Test
    @Transactional
    fun `deleteByUserId borra fisicamente todas las filas del usuario`() {
        // @Transactional aquí porque el `@Modifying @Query` de deleteByUserId necesita una transacción
        // activa para ejecutar; en producción la aporta el `@Transactional` de DeleteUserCommand.
        // Los demás tests de esta clase no la necesitan: `save`/`findLatestByUserId` ya llevan la suya
        // propia dentro de `SimpleJpaRepository`.
        val clubId = ClubId.of(UUID.randomUUID())
        val userId = UserId.of(UUID.randomUUID())
        autenticar(clubId, userId)
        repository.save(grant(userId, clubId))
        repository.save(grant(userId, clubId).revoke(Instant.now()))

        val borradas = repository.deleteByUserId(clubId, userId)

        borradas shouldBe 2
        jdbc.queryForObject(COUNT_SQL, Int::class.java, userId.value) shouldBe 0
    }

    private fun grant(
        userId: UserId,
        clubId: ClubId,
    ): Consent =
        Consent.grant(
            userId = userId,
            clubId = clubId,
            textVersion = ConsentText.CURRENT_VERSION,
            ip = "203.0.113.10",
            userAgent = "junit-agent/1.0",
            // Truncado a microsegundos: Postgres TIMESTAMPTZ solo guarda esa precisión, y comparar el
            // Instant original (con nanosegundos) contra el leído de la BD fallaría por los últimos
            // dígitos que el round-trip pierde.
            now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
        )

    private fun autenticar(
        clubId: ClubId,
        userId: UserId,
    ) {
        val principal = Principal(userId = userId.value, clubId = clubId.value, role = Role.ALUMNO)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        SecurityContextHolder.setContext(context)
    }

    private companion object {
        const val COUNT_SQL = "SELECT count(*) FROM identidad.consentimiento WHERE usuario_id = ?"
    }
}
