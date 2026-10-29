package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.clubtaxonomia.api.events.EntrenadorAsignadoAGrupo
import com.runcriticon.clubtaxonomia.api.events.EntrenadorEliminadoDeGrupo
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.OutboxDeliveries
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Flujo completo de `grupo_entrenador`: los eventos de asignación de `club_taxonomia` salen por el outbox tras el
 * commit y [CoachGroupProjectionListener] los aplica a la proyección con la que el panel de alertas acota "solo mis
 * grupos". Cada test espera a que la entrega termine antes de afirmar, también cuando afirma que no cambió nada.
 */
class CoachGroupProjectionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val deliveries by lazy { OutboxDeliveries(jdbc) }

    @Test
    fun `asignar un entrenador a un grupo proyecta su fila con el evento que la escribio`() {
        val club = UUID.randomUUID()
        val group = UUID.randomUUID()
        val coach = UUID.randomUUID()
        val assigned = assigned(club, group, coach, at = T1)

        deliver(assigned)

        val row = readRow(group, coach).shouldNotBeNull()
        row["club_id"] shouldBe club
        row["last_processed_event_id"] shouldBe assigned.eventId
        (row["last_processed_event_ts"] as Timestamp).toInstant() shouldBe T1
    }

    @Test
    fun `quitar al entrenador del grupo borra su fila`() {
        val club = UUID.randomUUID()
        val group = UUID.randomUUID()
        val coach = UUID.randomUUID()
        deliver(assigned(club, group, coach, at = T1))

        deliver(removed(club, group, coach, at = T2))

        readRow(group, coach).shouldBeNull()
    }

    @Test
    fun `reentregar la misma asignacion deja una sola fila y un solo evento procesado`() {
        val club = UUID.randomUUID()
        val group = UUID.randomUUID()
        val coach = UUID.randomUUID()
        val event = assigned(club, group, coach, at = T1)

        deliver(event)
        // Misma instancia, mismo eventId: es lo que hace un reintento del outbox.
        publish(event)
        deliveries.awaitCompleted(CoachGroupProjectionListener::class, event.eventId, times = 2)

        countRows(group, coach) shouldBe 1
        countProcessed(event.eventId) shouldBe 1
    }

    @Test
    fun `una asignacion mas antigua llegada despues no pisa la fila`() {
        val club = UUID.randomUUID()
        val group = UUID.randomUUID()
        val coach = UUID.randomUUID()
        val recent = assigned(club, group, coach, at = T2)
        deliver(recent)

        deliver(assigned(club, group, coach, at = T1))

        val row = readRow(group, coach).shouldNotBeNull()
        row["last_processed_event_id"] shouldBe recent.eventId
        (row["last_processed_event_ts"] as Timestamp).toInstant() shouldBe T2
    }

    @Test
    fun `una baja mas antigua llegada despues no borra una asignacion mas reciente`() {
        val club = UUID.randomUUID()
        val group = UUID.randomUUID()
        val coach = UUID.randomUUID()
        deliver(assigned(club, group, coach, at = T2))

        deliver(removed(club, group, coach, at = T1))

        readRow(group, coach).shouldNotBeNull()
    }

    /**
     * El desorden que la guarda de orden promete cubrir: el outbox entrega la baja antes que la asignación que la
     * precedió. El entrenador ya no lleva el grupo y no debe volver a verlo en su panel.
     */
    @Test
    fun `una asignacion que llega despues de su baja no resucita la fila`() {
        val club = UUID.randomUUID()
        val group = UUID.randomUUID()
        val coach = UUID.randomUUID()
        deliver(removed(club, group, coach, at = T2))

        deliver(assigned(club, group, coach, at = T1))

        readRow(group, coach).shouldBeNull()
    }

    private fun deliver(event: IntegrationEvent) {
        publish(event)
        deliveries.awaitCompleted(CoachGroupProjectionListener::class, event.eventId)
    }

    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun readRow(
        group: UUID,
        coach: UUID,
    ): Map<String, Any?>? =
        jdbc
            .queryForList(
                "SELECT * FROM seguimiento.grupo_entrenador WHERE grupo_id = ? AND entrenador_id = ?",
                group,
                coach,
            ).firstOrNull()

    private fun countRows(
        group: UUID,
        coach: UUID,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.grupo_entrenador WHERE grupo_id = ? AND entrenador_id = ?",
            Int::class.java,
            group,
            coach,
        ) ?: 0

    private fun countProcessed(eventId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.evento_procesado WHERE listener = ? AND event_id = ?",
            Int::class.java,
            "CoachGroupProjectionListener",
            eventId,
        ) ?: 0

    private companion object {
        val T1: Instant = Instant.parse("2026-09-04T10:00:00Z")
        val T2: Instant = Instant.parse("2026-09-04T11:00:00Z")
    }
}

private fun assigned(
    club: UUID,
    group: UUID,
    coach: UUID,
    at: Instant,
) = EntrenadorAsignadoAGrupo(
    eventId = UUID.randomUUID(),
    aggregateId = coach,
    occurredAt = at,
    clubId = club,
    actorId = null,
    traceparent = null,
    groupId = group,
)

private fun removed(
    club: UUID,
    group: UUID,
    coach: UUID,
    at: Instant,
) = EntrenadorEliminadoDeGrupo(
    eventId = UUID.randomUUID(),
    aggregateId = coach,
    occurredAt = at,
    clubId = club,
    actorId = null,
    traceparent = null,
    groupId = group,
)
