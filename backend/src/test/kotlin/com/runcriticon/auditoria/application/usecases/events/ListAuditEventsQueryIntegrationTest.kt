package com.runcriticon.auditoria.application.usecases.events

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventFilter
import com.runcriticon.auditoria.domain.AuditEventType
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/** Filtros y scoping por `club_id` contra Postgres real — la mecánica que `ListAuditEventsQueryTest` (unitario,
 * repo en memoria) no puede probar. `AuthEventRepositoryImpl.search` lleva `@AuthScope(Scope.CLUB)`, así que el
 * aspecto de verificación exige un principal real en el `SecurityContext`, no solo el `actor` del caso de uso. */
class ListAuditEventsQueryIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var query: ListAuditEventsQuery

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

    @BeforeEach
    fun autenticar() {
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                admin,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${admin.role.name}")),
            )
        SecurityContextHolder.setContext(context)
    }

    @AfterEach
    fun limpiarContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `solo devuelve eventos del club del principal, nunca de otro`() {
        val propio = sembrar(club.value, actorId = UUID.randomUUID(), tipo = "ACCESO_DENEGADO")
        sembrar(UUID.randomUUID(), actorId = UUID.randomUUID(), tipo = "ACCESO_DENEGADO")

        val result = query.execute(admin, AuditEventFilter()).shouldBeRight()

        result.map { it.id.value } shouldContainExactly listOf(propio)
    }

    @Test
    fun `filtra por actorId`() {
        val actor = UUID.randomUUID()
        val propio = sembrar(club.value, actorId = actor, tipo = "ACCESO_DENEGADO")
        sembrar(club.value, actorId = UUID.randomUUID(), tipo = "ACCESO_DENEGADO")

        val result = query.execute(admin, AuditEventFilter(actorId = actor)).shouldBeRight()

        result.map { it.id.value } shouldContainExactly listOf(propio)
    }

    @Test
    fun `filtra por tipo`() {
        val denegado = sembrar(club.value, actorId = UUID.randomUUID(), tipo = "ACCESO_DENEGADO")
        sembrar(club.value, actorId = UUID.randomUUID(), tipo = "ACCESO_DATOS_SENSIBLES")

        val result =
            query
                .execute(admin, AuditEventFilter(type = AuditEventType.ACCESO_DENEGADO))
                .shouldBeRight()

        result.map { it.id.value } shouldContainExactly listOf(denegado)
    }

    @Test
    fun `filtra por ventana temporal`() {
        val viejo = sembrar(club.value, actorId = UUID.randomUUID(), tipo = "ACCESO_DENEGADO", ts = OLD_TS)
        val nuevo = sembrar(club.value, actorId = UUID.randomUUID(), tipo = "ACCESO_DENEGADO", ts = RECENT_TS)

        val soloReciente =
            query.execute(admin, AuditEventFilter(desde = Instant.parse("2026-01-01T00:00:00Z"))).shouldBeRight()
        soloReciente.map { it.id.value } shouldContainExactly listOf(nuevo)

        val ambos = query.execute(admin, AuditEventFilter()).shouldBeRight()
        ambos shouldHaveSize 2
        // Más reciente primero.
        ambos.first().id.value shouldBe nuevo
        ambos.last().id.value shouldBe viejo
    }

    private fun sembrar(
        clubId: UUID,
        actorId: UUID,
        tipo: String,
        ts: Instant = Instant.now(),
    ): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            """
            INSERT INTO auditoria.evento (id, club_id, tipo, actor_id, sujeto_id, recurso, motivo, ts)
            VALUES (?, ?, ?, ?, NULL, 'PLAN:PUBLISH', 'RBAC', ?)
            """.trimIndent(),
            id,
            clubId,
            tipo,
            actorId,
            Timestamp.from(ts),
        )
        return id
    }

    private companion object {
        val OLD_TS: Instant = Instant.parse("2020-01-01T00:00:00Z")
        val RECENT_TS: Instant = Instant.parse("2026-08-19T00:00:00Z")
    }
}
