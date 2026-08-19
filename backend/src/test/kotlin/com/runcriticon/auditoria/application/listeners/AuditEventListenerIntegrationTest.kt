package com.runcriticon.auditoria.application.listeners

import com.runcriticon.auditoria.api.events.AccesoADatosSensibles
import com.runcriticon.auditoria.api.events.AccesoDenegado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Flujo completo de extremo a extremo: publicar en la transacción de un caso de uso, dejar que el outbox lo
 * entregue tras el commit, y comprobar la fila final en `auditoria.evento`. Mismo patrón que
 * `GroupMembersProjectionEventFlowIntegrationTest` de `planificacion`.
 *
 * `auditoria.evento` no guarda el `eventId` del integration event (su `id` propio lo genera el mapper) — cada
 * test usa un `recurso` marcador único para localizar su propia fila sin ambigüedad frente a otros tests que
 * comparten el mismo contenedor Postgres.
 */
class AuditEventListenerIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `un AccesoDenegado se persiste como fila ACCESO_DENEGADO`() {
        val actorId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        val recurso = marcador()

        publish(accesoDenegado(clubId, actorId, recurso))

        val fila = awaitRow(recurso)
        fila["tipo"] shouldBe "ACCESO_DENEGADO"
        fila["actor_id"] shouldBe actorId
        fila["motivo"] shouldBe "NotCoachOfGroup"
    }

    @Test
    fun `un AccesoADatosSensibles se persiste como fila ACCESO_DATOS_SENSIBLES`() {
        val sujetoId = UUID.randomUUID()
        val recurso = marcador()

        publish(accesoADatosSensibles(sujetoId, recurso))

        val fila = awaitRow(recurso)
        fila["tipo"] shouldBe "ACCESO_DATOS_SENSIBLES"
        fila["sujeto_id"] shouldBe sujetoId
        fila["motivo"] shouldBe null
    }

    @Test
    fun `reentregar el mismo AccesoDenegado no duplica la fila`() {
        val recurso = marcador()
        val evento = accesoDenegado(UUID.randomUUID(), UUID.randomUUID(), recurso)
        publish(evento)
        awaitRow(recurso)

        // Misma instancia, mismo eventId: es lo que hace un reintento del outbox.
        publish(evento)

        Thread.sleep(SETTLE_MILLIS)
        countRows(recurso) shouldBe 1
    }

    /** Recurso único por test: `auditoria.evento` no indexa por `eventId`, así que sirve de clave de búsqueda. */
    private fun marcador(): String = "TEST:${UUID.randomUUID()}"

    private fun accesoDenegado(
        clubId: UUID,
        actorId: UUID,
        recurso: String,
    ) = AccesoDenegado(
        eventId = UUID.randomUUID(),
        aggregateId = UUID.randomUUID(),
        occurredAt = Instant.now(),
        clubId = clubId,
        actorId = actorId,
        traceparent = null,
        recurso = recurso,
        motivo = "NotCoachOfGroup",
    )

    private fun accesoADatosSensibles(
        sujetoId: UUID,
        recurso: String,
    ) = AccesoADatosSensibles(
        eventId = UUID.randomUUID(),
        aggregateId = sujetoId,
        occurredAt = Instant.now(),
        clubId = UUID.randomUUID(),
        actorId = UUID.randomUUID(),
        traceparent = null,
        recurso = recurso,
        sujetoId = sujetoId,
    )

    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun awaitRow(recurso: String): Map<String, Any?> =
        await("no se proyectó la fila de recurso $recurso") { readRow(recurso) }

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

    private fun readRow(recurso: String): Map<String, Any?>? =
        jdbc
            .queryForList("SELECT * FROM auditoria.evento WHERE recurso = ?", recurso)
            .firstOrNull()

    private fun countRows(recurso: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM auditoria.evento WHERE recurso = ?",
            Int::class.java,
            recurso,
        ) ?: 0

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
        const val SETTLE_MILLIS = 500L
    }
}
