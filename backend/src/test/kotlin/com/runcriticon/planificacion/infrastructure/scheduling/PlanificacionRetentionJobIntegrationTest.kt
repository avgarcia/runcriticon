package com.runcriticon.planificacion.infrastructure.scheduling

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Contrato del job de retención de `planificacion.evento_procesado` contra Postgres real (ADR-0017 D4, D7): marcas
 * fuera de la ventana de 30 días se purgan, marcas dentro se conservan. Cada test siembra su propio `listener`
 * fresco y filtra por él (`docs/arquitectura/testing-de-modulos.md`).
 */
class PlanificacionRetentionJobIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var job: PlanificacionRetentionJob

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `purga marcas de idempotencia mas antiguas que la retencion y conserva las recientes`() {
        val listener = "RetentionJobTest-${UuidCreator.getTimeOrderedEpoch()}"
        val viejo = UuidCreator.getTimeOrderedEpoch()
        val reciente = UuidCreator.getTimeOrderedEpoch()
        sembrarEventoProcesado(listener, viejo, TREINTA_Y_UN_DIAS)
        sembrarEventoProcesado(listener, reciente, UN_DIA)

        job.purge()

        contarEventoProcesado(listener, viejo) shouldBe 0
        contarEventoProcesado(listener, reciente) shouldBe 1
    }

    @Test
    fun `una marca de idempotencia justo dentro de la ventana de 30 dias no se purga`() {
        val listener = "RetentionJobTest-${UuidCreator.getTimeOrderedEpoch()}"
        val eventId = UuidCreator.getTimeOrderedEpoch()
        sembrarEventoProcesado(listener, eventId, VEINTINUEVE_DIAS)

        job.purge()

        contarEventoProcesado(listener, eventId) shouldBe 1
    }

    private fun sembrarEventoProcesado(
        listener: String,
        eventId: UUID,
        antiguedad: Duration,
    ) {
        jdbc.update(
            "INSERT INTO planificacion.evento_procesado (listener, event_id, processed_at) VALUES (?, ?, ?)",
            listener,
            eventId,
            Timestamp.from(Instant.now().minus(antiguedad)),
        )
    }

    private fun contarEventoProcesado(
        listener: String,
        eventId: UUID,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM planificacion.evento_procesado WHERE listener = ? AND event_id = ?",
            Int::class.java,
            listener,
            eventId,
        ) ?: 0

    private companion object {
        val UN_DIA: Duration = Duration.ofDays(1)
        val VEINTINUEVE_DIAS: Duration = Duration.ofDays(29)
        val TREINTA_Y_UN_DIAS: Duration = Duration.ofDays(31)
    }
}
