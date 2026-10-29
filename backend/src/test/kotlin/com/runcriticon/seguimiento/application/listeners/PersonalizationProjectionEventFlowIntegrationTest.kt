package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.planificacion.api.events.PersonalizacionAplicada
import com.runcriticon.planificacion.api.events.PersonalizacionRetirada
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Flujo real de `PersonalizationProjectionListener` contra Postgres y el outbox: la guarda de orden del
 * `UPDATE`, por qué no tocó filas, y sobre todo que una personalización que llega antes que su
 * `PlanPublicado` no se pierde. El listener lanza, la transacción deshace la marca de `evento_procesado` y la
 * publicación queda `FAILED`; al reentregarla (lo que hace el arranque con
 * `republish-outstanding-events-on-restart`, aquí con [IncompleteEventPublications]) se aplica.
 */
class PersonalizationProjectionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var incompletePublications: IncompleteEventPublications

    private val clubId = UUID.randomUUID()
    private val dia = LocalDate.parse("2026-08-17")

    @Test
    fun `con el plan ya proyectado la personalizacion sustituye la sesion del alumno`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        publishPlan(planId, listOf(alumno))

        val aplicada = personalizacionAplicada(planId, alumno)
        publish(aplicada)

        awaitCompleted(aplicada.eventId)
        val row = readRow(planId, alumno)
        row["es_personalizada"] shouldBe true
        row["mensaje_al_alumno"] shouldBe MENSAJE
        row["last_processed_event_id"] shouldBe aplicada.eventId
        (row["last_processed_event_ts"] as Timestamp).toInstant() shouldBe aplicada.occurredAt
    }

    @Test
    fun `una personalizacion que llega antes que su plan no se pierde - el outbox la reentrega y se aplica`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val aplicada = personalizacionAplicada(planId, alumno)

        publish(aplicada)

        awaitFailed(aplicada.eventId)
        countProcessed(aplicada.eventId) shouldBe 0

        publishPlan(planId, listOf(alumno))
        incompletePublications.resubmitIncompletePublications { publication ->
            (publication.event as? IntegrationEvent)?.eventId == aplicada.eventId
        }

        awaitCompleted(aplicada.eventId)
        countProcessed(aplicada.eventId) shouldBe 1
        val row = readRow(planId, alumno)
        row["es_personalizada"] shouldBe true
        row["mensaje_al_alumno"] shouldBe MENSAJE
    }

    @Test
    fun `una personalizacion de un alumno sin fila en el plan proyectado se confirma sin escribir nada`() {
        val planId = UUID.randomUUID()
        val alumnoEnElPlan = UUID.randomUUID()
        val alumnoSuprimido = UUID.randomUUID()
        publishPlan(planId, listOf(alumnoEnElPlan))

        val aplicada = personalizacionAplicada(planId, alumnoSuprimido)
        publish(aplicada)

        awaitCompleted(aplicada.eventId)
        countProcessed(aplicada.eventId) shouldBe 1
        countRows(planId) shouldBe 1
    }

    @Test
    fun `una personalizacion mas antigua que la ultima escritura de la fila se descarta y se confirma`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        publishPlan(planId, listOf(alumno))
        val retirada = personalizacionRetirada(planId, alumno, occurredAt = Instant.parse("2026-08-15T10:00:00Z"))
        publish(retirada)
        awaitCompleted(retirada.eventId)

        val aplicadaAnterior =
            personalizacionAplicada(planId, alumno, occurredAt = Instant.parse("2026-08-14T10:00:00Z"))
        publish(aplicadaAnterior)

        awaitCompleted(aplicadaAnterior.eventId)
        countProcessed(aplicadaAnterior.eventId) shouldBe 1
        val row = readRow(planId, alumno)
        row["es_personalizada"] shouldBe false
        // La fila sigue recogiendo la retirada: el evento descartado no la marca como suya.
        row["last_processed_event_id"] shouldBe retirada.eventId
        (row["last_processed_event_ts"] as Timestamp).toInstant() shouldBe retirada.occurredAt
    }

    private fun publishPlan(
        planId: UUID,
        students: List<UUID>,
    ) {
        val plan = planPublicado(planId, students)
        publish(plan)
        await("no se proyectó el plan $planId") { countRows(planId).takeIf { it == students.size } }
    }

    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun awaitCompleted(eventId: UUID) =
        await("PersonalizationProjectionListener no completó $eventId") {
            publicationsOf(eventId, "completion_date IS NOT NULL").takeIf { it == 1 }
        }

    private fun awaitFailed(eventId: UUID) =
        await("PersonalizationProjectionListener no dejó $eventId en FAILED") {
            publicationsOf(eventId, "completion_date IS NULL AND status = 'FAILED'").takeIf { it == 1 }
        }

    private fun publicationsOf(
        eventId: UUID,
        condition: String,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ? " +
                "AND listener_id LIKE '%PersonalizationProjectionListener%' AND $condition",
            Int::class.java,
            "%$eventId%",
        ) ?: 0

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

    private fun readRow(
        planId: UUID,
        alumnoId: UUID,
    ): Map<String, Any?> =
        jdbc.queryForMap(
            "SELECT * FROM seguimiento.plan_resuelto_por_alumno WHERE plan_id = ? AND alumno_id = ? AND dia = ?",
            planId,
            alumnoId,
            dia,
        )

    private fun countRows(planId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.plan_resuelto_por_alumno WHERE plan_id = ?",
            Int::class.java,
            planId,
        ) ?: 0

    private fun countProcessed(eventId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.evento_procesado " +
                "WHERE listener = 'PersonalizationProjectionListener' AND event_id = ?",
            Int::class.java,
            eventId,
        ) ?: 0

    private fun planPublicado(
        planId: UUID,
        students: List<UUID>,
    ) = PlanPublicado(
        eventId = UUID.randomUUID(),
        aggregateId = planId,
        occurredAt = Instant.parse("2026-08-13T10:00:00Z"),
        clubId = clubId,
        actorId = null,
        traceparent = null,
        grupoId = UUID.randomUUID(),
        snapshotAlumnos = students,
        sesiones =
            listOf(
                PublishedSession(
                    dia = dia,
                    tipo = "RODAJE",
                    volumenTipo = null,
                    volumenMetros = null,
                    volumenMinutos = null,
                    ritmoTipo = null,
                    ritmoSegundosPorKm = null,
                    ritmoReferencia = null,
                    ritmoDeltaSegundosPorKm = null,
                    notas = null,
                ),
            ),
        personalizaciones = emptyList(),
    )

    private fun personalizacionAplicada(
        planId: UUID,
        alumno: UUID,
        occurredAt: Instant = Instant.parse("2026-08-14T10:00:00Z"),
    ) = PersonalizacionAplicada(
        eventId = UUID.randomUUID(),
        aggregateId = planId,
        occurredAt = occurredAt,
        clubId = clubId,
        actorId = null,
        traceparent = null,
        grupoId = UUID.randomUUID(),
        sesionId = UUID.randomUUID(),
        dia = dia,
        alumnoId = alumno,
        override = personalizedSession("DESCANSO"),
        mensajeAlAlumno = MENSAJE,
    )

    private fun personalizacionRetirada(
        planId: UUID,
        alumno: UUID,
        occurredAt: Instant,
    ) = PersonalizacionRetirada(
        eventId = UUID.randomUUID(),
        aggregateId = planId,
        occurredAt = occurredAt,
        clubId = clubId,
        actorId = null,
        traceparent = null,
        grupoId = UUID.randomUUID(),
        sesionId = UUID.randomUUID(),
        dia = dia,
        alumnoId = alumno,
        baseSession = personalizedSession("RODAJE"),
    )

    private fun personalizedSession(tipo: String) =
        PersonalizedSession(
            tipo = tipo,
            volumenTipo = null,
            volumenMetros = null,
            volumenMinutos = null,
            ritmoTipo = null,
            ritmoSegundosPorKm = null,
            ritmoReferencia = null,
            ritmoDeltaSegundosPorKm = null,
            notas = null,
        )

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
        const val MENSAJE = "Hoy descansa, mañana rodaje suave"
    }
}
