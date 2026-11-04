package com.runcriticon.identidad.infrastructure.scheduling

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
 * Contrato del job de retención de `identidad` contra Postgres real (ADR-0017 D7, D8): los asientos de
 * `evento_auditoria` fuera de la ventana de 12 meses (ADR-0014 D10 categoría 2) y las marcas de `evento_procesado`
 * fuera de la de 30 días se purgan, y las que siguen dentro se conservan. Cada test siembra su propio `id` (o
 * `listener`) fresco y filtra por él (`docs/arquitectura/testing-de-modulos.md`).
 */
class IdentidadRetentionJobIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var job: IdentidadRetentionJob

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `purga asientos mas antiguos que la retencion y conserva los recientes`() {
        val viejo = UuidCreator.getTimeOrderedEpoch()
        val reciente = UuidCreator.getTimeOrderedEpoch()
        sembrarAsiento(viejo, TRECE_MESES)
        sembrarAsiento(reciente, UN_MES)

        job.purge()

        contarAsiento(viejo) shouldBe 0
        contarAsiento(reciente) shouldBe 1
    }

    @Test
    fun `un asiento justo dentro de la ventana de 12 meses no se purga`() {
        val id = UuidCreator.getTimeOrderedEpoch()
        sembrarAsiento(id, ONCE_MESES)

        job.purge()

        contarAsiento(id) shouldBe 1
    }

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
            "INSERT INTO identidad.evento_procesado (listener, event_id, processed_at) VALUES (?, ?, ?)",
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
            "SELECT count(*) FROM identidad.evento_procesado WHERE listener = ? AND event_id = ?",
            Int::class.java,
            listener,
            eventId,
        ) ?: 0

    private fun sembrarAsiento(
        id: UUID,
        antiguedad: Duration,
    ) {
        jdbc.update(
            """
            INSERT INTO identidad.evento_auditoria (id, tipo, ts)
            VALUES (?, 'LOGIN_OK', ?)
            """.trimIndent(),
            id,
            Timestamp.from(Instant.now().minus(antiguedad)),
        )
    }

    private fun contarAsiento(id: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM identidad.evento_auditoria WHERE id = ?",
            Int::class.java,
            id,
        ) ?: 0

    private companion object {
        val UN_DIA: Duration = Duration.ofDays(1)
        val VEINTINUEVE_DIAS: Duration = Duration.ofDays(29)
        val TREINTA_Y_UN_DIAS: Duration = Duration.ofDays(31)
        val UN_MES: Duration = Duration.ofDays(30)
        val ONCE_MESES: Duration = Duration.ofDays(11 * 30L)
        val TRECE_MESES: Duration = Duration.ofDays(13 * 30L)
    }
}
