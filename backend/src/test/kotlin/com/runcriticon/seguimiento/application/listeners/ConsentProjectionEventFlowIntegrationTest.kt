package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.identidad.api.events.ConsentimientoConcedido
import com.runcriticon.identidad.api.events.ConsentimientoRevocado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.OutboxDeliveries
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Flujo completo de extremo a extremo (consentimiento explícito Art. 9.2.a): publicar
 * `ConsentimientoConcedido`/`ConsentimientoRevocado`
 * en la transacción de un caso de uso, dejar que el outbox lo entregue tras el commit, y comprobar el estado
 * final de `consentimiento_alumno`. Mismo patrón que `ResolvedPlanProjectionEventFlowIntegrationTest`.
 */
class ConsentProjectionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `conceder consentimiento proyecta vigente=true con la version del texto`() {
        val alumno = UUID.randomUUID()
        val club = UUID.randomUUID()

        val event = concedido(alumno, club, textVersion = "v2026-08-25")

        publish(event)

        val row = awaitRow(alumno)
        row["vigente"] shouldBe true
        row["version_texto"] shouldBe "v2026-08-25"
        row["club_id"] shouldBe club
        row["last_processed_event_id"] shouldBe event.eventId
        (row["last_processed_event_ts"] as Timestamp).toInstant() shouldBe event.occurredAt
    }

    @Test
    fun `revocar consentimiento proyecta vigente=false`() {
        val alumno = UUID.randomUUID()
        val club = UUID.randomUUID()
        publish(concedido(alumno, club))
        awaitRow(alumno)

        publish(revocado(alumno, club, occurredAt = Instant.parse("2026-08-25T11:00:00Z")))

        await("no se proyectó la revocación de $alumno") {
            readRow(alumno)?.takeIf { it["vigente"] == false }
        }
    }

    @Test
    fun `un evento mas antiguo llegado tras uno mas reciente no revierte la proyeccion`() {
        val alumno = UUID.randomUUID()
        val club = UUID.randomUUID()
        publish(concedido(alumno, club, occurredAt = Instant.parse("2026-08-25T10:00:00Z")))
        awaitRow(alumno)
        val revocation = revocado(alumno, club, occurredAt = Instant.parse("2026-08-25T11:00:00Z"))
        publish(revocation)
        await("no se proyectó la revocación de $alumno") { readRow(alumno)?.takeIf { it["vigente"] == false } }

        // Reentrega tardía de la concesión original, con un occurredAt anterior a la revocación ya aplicada:
        // la guarda de orden (WHERE last_processed_event_ts <= ?) debe descartarla.
        val stale =
            concedido(alumno, club, occurredAt = Instant.parse("2026-08-25T10:00:00Z"), eventId = UUID.randomUUID())
        publish(stale)

        OutboxDeliveries(jdbc).awaitCompleted(ConsentProjectionListener::class, stale.eventId)
        val row = readRow(alumno).shouldNotBeNull()
        row["vigente"] shouldBe false
        row["last_processed_event_id"] shouldBe revocation.eventId
        (row["last_processed_event_ts"] as Timestamp).toInstant() shouldBe revocation.occurredAt
    }

    @Test
    fun `reentregar el mismo evento no duplica ni cambia nada`() {
        val alumno = UUID.randomUUID()
        val club = UUID.randomUUID()
        val event = concedido(alumno, club)

        publish(event)
        awaitRow(alumno)
        publish(event)

        OutboxDeliveries(jdbc).awaitCompleted(ConsentProjectionListener::class, event.eventId, times = 2)
        countRows(alumno) shouldBe 1
        countProcessed(event.eventId) shouldBe 1
    }

    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun awaitRow(alumnoId: UUID): Map<String, Any?> =
        await("no se proyectó la fila de $alumnoId") { readRow(alumnoId) }

    private fun <T> await(
        failure: String,
        probe: () -> T?,
    ): T {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(DEADLINE_SECONDS).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            probe()?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("$failure en $DEADLINE_SECONDS s")
    }

    private fun readRow(alumnoId: UUID): Map<String, Any?>? =
        jdbc
            .queryForList("SELECT * FROM seguimiento.consentimiento_alumno WHERE alumno_id = ?", alumnoId)
            .firstOrNull()

    private fun countRows(alumnoId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.consentimiento_alumno WHERE alumno_id = ?",
            Int::class.java,
            alumnoId,
        ) ?: 0

    private fun countProcessed(eventId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.evento_procesado WHERE event_id = ?",
            Int::class.java,
            eventId,
        ) ?: 0

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
    }
}

private fun concedido(
    alumno: UUID,
    club: UUID,
    textVersion: String = "v2026-08-25",
    occurredAt: Instant = Instant.parse("2026-08-25T10:00:00Z"),
    eventId: UUID = UUID.randomUUID(),
) = ConsentimientoConcedido(
    eventId = eventId,
    aggregateId = alumno,
    occurredAt = occurredAt,
    clubId = club,
    actorId = alumno,
    traceparent = null,
    versionTexto = textVersion,
)

private fun revocado(
    alumno: UUID,
    club: UUID,
    occurredAt: Instant = Instant.parse("2026-08-25T11:00:00Z"),
) = ConsentimientoRevocado(
    eventId = UUID.randomUUID(),
    aggregateId = alumno,
    occurredAt = occurredAt,
    clubId = club,
    actorId = alumno,
    traceparent = null,
)
