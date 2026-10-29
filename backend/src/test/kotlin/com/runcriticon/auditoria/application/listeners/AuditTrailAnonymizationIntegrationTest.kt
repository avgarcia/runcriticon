package com.runcriticon.auditoria.application.listeners

import com.runcriticon.identidad.api.events.AdminEliminado
import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.OutboxDeliveries
import io.kotest.matchers.shouldBe
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

    private val deliveries by lazy { OutboxDeliveries(jdbc) }

    @Test
    fun `borrar al alumno anonimiza las filas donde aparece como actor o como sujeto`() {
        val alumno = UUID.randomUUID()
        val filaComoActor = sembrarFila(actorId = alumno, sujetoId = null)
        val filaComoSujeto = sembrarFila(actorId = UUID.randomUUID(), sujetoId = alumno)
        val filaAjena = sembrarFila(actorId = UUID.randomUUID(), sujetoId = UUID.randomUUID())

        val evento = alumnoEliminado(alumno)

        deliver(evento)

        leerFila(filaComoActor).anonimizada() shouldBe true
        // Solo `sujeto_id`, no ambas columnas: `filaComoSujeto` lleva un `actor_id` de un tercero (ver el test
        // dedicado más abajo, que comprueba explícitamente que ese id sobrevive).
        leerFila(filaComoSujeto)["sujeto_id"] shouldBe null
        // La fila que no menciona al alumno no debe tocarse.
        leerFila(filaAjena)["actor_id"] shouldNotBe null
        leerFila(filaAjena)["sujeto_id"] shouldNotBe null
    }

    /** Un entrenador aparece como actor de lo que hizo y como sujeto de lo que otros hicieron sobre él. */
    @Test
    fun `borrar a un entrenador anonimiza las filas donde aparece como actor o como sujeto`() {
        val entrenador = UUID.randomUUID()
        val filaComoActor = sembrarFila(actorId = entrenador, sujetoId = UUID.randomUUID())
        val filaComoSujeto = sembrarFila(actorId = UUID.randomUUID(), sujetoId = entrenador)
        val filaAjena = sembrarFila(actorId = UUID.randomUUID(), sujetoId = UUID.randomUUID())

        deliver(entrenadorEliminado(entrenador))

        leerFila(filaComoActor)["actor_id"] shouldBe null
        leerFila(filaComoSujeto)["sujeto_id"] shouldBe null
        leerFila(filaAjena)["actor_id"] shouldNotBe null
        leerFila(filaAjena)["sujeto_id"] shouldNotBe null
    }

    /**
     * Anonimizar dos veces deja las mismas filas que una, así que el único efecto visible de la idempotencia es el
     * asiento en `evento_procesado`: uno solo para las dos entregas.
     */
    @Test
    fun `reentregar la misma baja la marca como procesada una sola vez`() {
        val alumno = UUID.randomUUID()
        val fila = sembrarFila(actorId = alumno, sujetoId = null)
        val evento = alumnoEliminado(alumno)

        deliver(evento)
        // Misma instancia, mismo eventId: es lo que hace un reintento del outbox.
        publish(evento)
        deliveries.awaitCompleted(AuditTrailAnonymizationListener::class, evento.eventId, times = 2)

        leerFila(fila).anonimizada() shouldBe true
        jdbc.queryForObject(
            "SELECT count(*) FROM auditoria.evento_procesado WHERE listener = ? AND event_id = ?",
            Int::class.java,
            "AuditTrailAnonymizationListener",
            evento.eventId,
        ) shouldBe 1
    }

    /**
     * El caso que justifica el `CASE` por columna: anonimizar al alumno como `sujeto_id` no debe despojar
     * el `actor_id` de un tercero que no ha pedido nada — el entrenador que ejecutó la acción denegada.
     */
    @Test
    fun `anonimizar al sujeto no despoja el actor_id de un tercero`() {
        val alumno = UUID.randomUUID()
        val entrenador = UUID.randomUUID()
        val fila = sembrarFila(actorId = entrenador, sujetoId = alumno)

        publish(alumnoEliminado(alumno))

        awaitAnonimizada(fila) { it["actor_id"] == entrenador && it["sujeto_id"] == null }
        leerFila(fila)["actor_id"] shouldBe entrenador
    }

    /**
     * Un admin puede ser `actor_id` de un `ACCESO_DENEGADO` (a él también se le puede denegar un acceso).
     * Antes de esta PR, `DeleteUserCommand` no publicaba ningún evento para un ADMIN, así que este asiento nunca se
     * anonimizaba.
     */
    @Test
    fun `borrar a un admin anonimiza las filas donde aparece como actor`() {
        val admin = UUID.randomUUID()
        val fila = sembrarFila(actorId = admin, sujetoId = null)

        publish(adminEliminado(admin))

        awaitAnonimizada(fila)
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

    private fun entrenadorEliminado(entrenadorId: UUID) =
        EntrenadorEliminado(
            eventId = UUID.randomUUID(),
            aggregateId = entrenadorId,
            occurredAt = Instant.now(),
            clubId = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            traceparent = null,
        )

    private fun adminEliminado(adminId: UUID) =
        AdminEliminado(
            eventId = UUID.randomUUID(),
            aggregateId = adminId,
            occurredAt = Instant.now(),
            clubId = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            traceparent = null,
        )

    private fun deliver(event: IntegrationEvent) {
        publish(event)
        deliveries.awaitCompleted(AuditTrailAnonymizationListener::class, event.eventId)
    }

    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun Map<String, Any?>.anonimizada() = this["actor_id"] == null && this["sujeto_id"] == null

    private fun awaitAnonimizada(
        id: UUID,
        cumple: (Map<String, Any?>) -> Boolean = { it["actor_id"] == null && it["sujeto_id"] == null },
    ) = await("la fila $id no se anonimizó") {
        val fila = leerFila(id)
        if (cumple(fila)) Unit else null
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
    }
}
