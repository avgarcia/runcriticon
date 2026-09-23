package com.runcriticon.clubtaxonomia.infrastructure.scheduling

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
 * Contrato del job de retención contra Postgres real (ADR-0017 D4, D7): filas fuera de la ventana de 30 días se
 * purgan, filas dentro se conservan. Ninguna de las dos tablas tiene `club_id` (son `SIN_PII`), así que el
 * aislamiento entre tests viene de sembrar con ids frescos y contar por ellos, no de truncar la tabla
 * (`docs/arquitectura/testing-de-modulos.md` — patrón fijado tras corregir que los tests de persona borraban sin filtro de club y contaminaban otras clases).
 */
class ClubTaxonomiaRetentionJobIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var job: ClubTaxonomiaRetentionJob

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `purga lapidas mas antiguas que la retencion y conserva las recientes`() {
        val vieja = UuidCreator.getTimeOrderedEpoch()
        val reciente = UuidCreator.getTimeOrderedEpoch()
        sembrarLapida(vieja, TREINTA_Y_UN_DIAS)
        sembrarLapida(reciente, UN_DIA)

        job.purge()

        contarLapida(vieja) shouldBe 0
        contarLapida(reciente) shouldBe 1
    }

    @Test
    fun `una lapida justo dentro de la ventana de 30 dias no se purga`() {
        val id = UuidCreator.getTimeOrderedEpoch()
        sembrarLapida(id, VEINTINUEVE_DIAS)

        job.purge()

        contarLapida(id) shouldBe 1
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

    private fun sembrarLapida(
        id: UUID,
        antiguedad: Duration,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.persona_eliminada (id, eliminado_en) VALUES (?, ?)",
            id,
            Timestamp.from(Instant.now().minus(antiguedad)),
        )
    }

    private fun sembrarEventoProcesado(
        listener: String,
        eventId: UUID,
        antiguedad: Duration,
    ) {
        jdbc.update(
            "INSERT INTO club_taxonomia.evento_procesado (listener, event_id, processed_at) VALUES (?, ?, ?)",
            listener,
            eventId,
            Timestamp.from(Instant.now().minus(antiguedad)),
        )
    }

    private fun contarLapida(id: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.persona_eliminada WHERE id = ?",
            Int::class.java,
            id,
        ) ?: 0

    private fun contarEventoProcesado(
        listener: String,
        eventId: UUID,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.evento_procesado WHERE listener = ? AND event_id = ?",
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
