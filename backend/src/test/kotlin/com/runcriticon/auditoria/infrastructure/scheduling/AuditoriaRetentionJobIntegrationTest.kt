package com.runcriticon.auditoria.infrastructure.scheduling

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
 * Contrato del job de retención de `auditoria` contra Postgres real (ADR-0017 D5, D7): los asientos de `evento`
 * fuera de la ventana de 24 meses y las marcas de `evento_procesado` fuera de la de 30 días se purgan, y las que
 * siguen dentro se conservan. Cada test siembra su propio `id` (o `listener`) fresco y filtra por
 * él, así que no le importa lo que dejen otros tests en el contenedor compartido (patrón fijado en
 * `docs/arquitectura/testing-de-modulos.md`, fijado tras corregir que los tests de persona borraban sin filtro de club
 * y contaminaban otras clases).
 */
class AuditoriaRetentionJobIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var job: AuditoriaRetentionJob

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `purga asientos mas antiguos que la retencion y conserva los recientes`() {
        val viejo = UuidCreator.getTimeOrderedEpoch()
        val reciente = UuidCreator.getTimeOrderedEpoch()
        sembrarAsiento(viejo, VEINTICINCO_MESES)
        sembrarAsiento(reciente, UN_MES)

        job.purge()

        contarAsiento(viejo) shouldBe 0
        contarAsiento(reciente) shouldBe 1
    }

    @Test
    fun `un asiento justo dentro de la ventana de 24 meses no se purga`() {
        val id = UuidCreator.getTimeOrderedEpoch()
        sembrarAsiento(id, VEINTITRES_MESES)

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
            "INSERT INTO auditoria.evento_procesado (listener, event_id, processed_at) VALUES (?, ?, ?)",
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
            "SELECT count(*) FROM auditoria.evento_procesado WHERE listener = ? AND event_id = ?",
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
            INSERT INTO auditoria.evento (id, club_id, tipo, recurso, ts)
            VALUES (?, ?, 'ACCESO_DENEGADO', 'PLAN', ?)
            """.trimIndent(),
            id,
            UuidCreator.getTimeOrderedEpoch(),
            Timestamp.from(Instant.now().minus(antiguedad)),
        )
    }

    private fun contarAsiento(id: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM auditoria.evento WHERE id = ?",
            Int::class.java,
            id,
        ) ?: 0

    private companion object {
        val UN_DIA: Duration = Duration.ofDays(1)
        val VEINTINUEVE_DIAS: Duration = Duration.ofDays(29)
        val TREINTA_Y_UN_DIAS: Duration = Duration.ofDays(31)
        val UN_MES: Duration = Duration.ofDays(30)
        val VEINTITRES_MESES: Duration = Duration.ofDays(23 * 30L)
        val VEINTICINCO_MESES: Duration = Duration.ofDays(25 * 30L)
    }
}
