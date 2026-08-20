package com.runcriticon.auditoria.application.listeners

import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Derecho al olvido sobre `auditoria.evento` (ADR-0009 D17): anonimiza, no borra. Siembra la fila directamente
 * por JDBC (más simple que producir un `AccesoDenegado` real solo para tener algo que anonimizar) y comprueba
 * el efecto del evento real de identidad tras el outbox.
 */
class AuditTrailAnonymizationIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `borrar al alumno anonimiza las filas donde aparece como actor o como sujeto`() {
        val alumno = UUID.randomUUID()
        val filaComoActor = sembrarFila(actorId = alumno, sujetoId = null)
        val filaComoSujeto = sembrarFila(actorId = UUID.randomUUID(), sujetoId = alumno)
        val filaAjena = sembrarFila(actorId = UUID.randomUUID(), sujetoId = UUID.randomUUID())

        publish(alumnoEliminado(alumno))

        awaitAnonimizada(filaComoActor)
        awaitAnonimizada(filaComoSujeto)

        // La fila que no menciona al alumno no debe tocarse.
        Thread.sleep(SETTLE_MILLIS)
        leerFila(filaAjena)["actor_id"] shouldNotBe null
    }

    private fun sembrarFila(
        actorId: UUID?,
        sujetoId: UUID?,
    ): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO auditoria.evento (id, club_id, tipo, actor_id, sujeto_id, recurso, motivo, ts)
            VALUES (?, ?, 'ACCESO_DENEGADO', ?, ?, 'PLAN:PUBLISH', 'NotCoachOfGroup', now())
            """.trimIndent(),
            id,
            UUID.randomUUID(),
            actorId,
            sujetoId,
        )
        return id
    }

    private fun alumnoEliminado(alumnoId: UUID) =
        AlumnoEliminado(
            eventId = UUID.randomUUID(),
            aggregateId = alumnoId,
            occurredAt = Instant.now(),
            clubId = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            traceparent = null,
        )

    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun awaitAnonimizada(id: UUID) =
        await("la fila $id no se anonimizó") {
            val fila = leerFila(id)
            if (fila["actor_id"] == null && fila["sujeto_id"] == null) Unit else null
        }

    private fun leerFila(id: UUID): Map<String, Any?> =
        jdbc.queryForList("SELECT * FROM auditoria.evento WHERE id = ?", id).single()

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

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
        const val SETTLE_MILLIS = 500L
    }
}
