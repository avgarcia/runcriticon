package com.runcriticon.clubtaxonomia.application.listeners

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Regresión de LAL-137: [MergeSuggestionListener] corre en su propio hilo, sin `Principal`/`SecurityContext`,
 * como cualquier `@ApplicationModuleListener` -- a diferencia de [MergeSuggestionRepositoryIntegrationTest], que
 * autentica un admin antes de llamar a `upsert`/`delete` directamente y por eso nunca vio fallar el
 * `@AuthScope(Scope.CLUB)` que llevaban esos dos métodos. Este test publica el evento de verdad, sin autenticar
 * nada, igual que [PersonProjectionEventFlowIntegrationTest] -- si `upsert`/`delete` volvieran a llevar
 * `@AuthScope`, `AuthScopeEnforcementAspect` fallaría cerrado y la fila nunca aparecería.
 */
class MergeSuggestionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var groups: GroupRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())

    @Test
    fun `un grupo con 0 alumnos genera una sugerencia MICRO sin principal autenticado`() {
        val grupo = crearGrupo("Grupo diminuto ${UuidCreator.getTimeOrderedEpoch()}")

        transactions.executeWithoutResult {
            events.publishEvent(
                MembresiaDeGrupoCambiada(
                    eventId = UUID.randomUUID(),
                    aggregateId = grupo.value,
                    occurredAt = Instant.now(),
                    clubId = club.value,
                    actorId = null,
                    traceparent = null,
                    alumnos = emptyList(),
                ),
            )
        }

        val fila = awaitSugerencia(grupo.value)
        fila["tipo"] shouldBe "MICRO"
    }

    /**
     * `GroupRepository.save` sí exige un principal autenticado (crear un grupo es una operación HTTP normal) --
     * a diferencia de la publicación del evento más abajo, que debe quedar sin autenticar para reproducir el
     * contexto real del listener. Se autentica solo para el `save` y se limpia enseguida.
     */
    private fun crearGrupo(nombre: String): GroupId {
        val group = Group.create(club, nombre).shouldBeRight()
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(admin, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
        SecurityContextHolder.setContext(context)
        try {
            transactions.executeWithoutResult { groups.save(club, group) }
        } finally {
            SecurityContextHolder.clearContext()
        }
        return group.id
    }

    private fun awaitSugerencia(groupId: UUID): Map<String, Any?> {
        val deadline = System.nanoTime() + Duration.ofSeconds(DEADLINE_SECONDS).toNanos()
        while (System.nanoTime() < deadline) {
            val filas =
                jdbc.queryForList(
                    "SELECT tipo FROM club_taxonomia.sugerencia_fusion_grupo " +
                        "WHERE club_id = ? AND grupo_id_a = ? AND grupo_id_b = ? AND tipo = 'MICRO'",
                    club.value,
                    groupId,
                    groupId,
                )
            filas.firstOrNull()?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("la sugerencia MICRO de $groupId no se materializó en ${DEADLINE_SECONDS}s")
    }

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
    }
}
