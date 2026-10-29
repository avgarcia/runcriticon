package com.runcriticon.planificacion.application.listeners

import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.OutboxDeliveries
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * Derecho de supresión en `planificacion` de extremo a extremo: `EntrenadorEliminado` sale por el outbox y
 * [PlanificacionDeletionListener] borra lo que este módulo guarda del entrenador. Siembra las filas por JDBC, como
 * `SeguimientoDeletionEventFlowIntegrationTest`.
 */
class PlanificacionDeletionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `un entrenador eliminado pierde sus filas de grupo y su marca de agua, sin tocar las de otro entrenador`() {
        val coach = UUID.randomUUID()
        val otherCoach = UUID.randomUUID()
        val club = UUID.randomUUID()
        val group = UUID.randomUUID()
        seedCoachMembership(group, coach, club)
        seedCoachMembership(group, otherCoach, club)
        val event =
            EntrenadorEliminado(
                eventId = UUID.randomUUID(),
                aggregateId = coach,
                occurredAt = Instant.now(),
                clubId = club,
                actorId = null,
                traceparent = null,
            )

        transactions.executeWithoutResult { events.publishEvent(event) }
        OutboxDeliveries(jdbc).awaitCompleted(PlanificacionDeletionListener::class, event.eventId)

        countRows("miembro_grupo", coach) shouldBe 0
        countRows("miembro_grupo_entrenador_version", coach) shouldBe 0
        countRows("miembro_grupo", otherCoach) shouldBe 1
        countRows("miembro_grupo_entrenador_version", otherCoach) shouldBe 1
    }

    /** La fila ENTRENADOR y su marca de agua, como las deja `GroupMembersProjectionJdbc` tras una asignación. */
    private fun seedCoachMembership(
        group: UUID,
        coach: UUID,
        club: UUID,
    ) {
        jdbc.update(
            """
            INSERT INTO planificacion.miembro_grupo
                (grupo_id, club_id, persona_id, rol, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, 'ENTRENADOR', ?, now())
            """.trimIndent(),
            group,
            club,
            coach,
            UUID.randomUUID(),
        )
        jdbc.update(
            """
            INSERT INTO planificacion.miembro_grupo_entrenador_version
                (grupo_id, persona_id, club_id, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, now())
            """.trimIndent(),
            group,
            coach,
            club,
            UUID.randomUUID(),
        )
    }

    private fun countRows(
        table: String,
        person: UUID,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM planificacion.$table WHERE persona_id = ?",
            Int::class.java,
            person,
        ) ?: 0
}
