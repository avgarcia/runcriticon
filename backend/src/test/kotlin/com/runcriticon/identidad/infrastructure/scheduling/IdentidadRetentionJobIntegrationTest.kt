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
 * Contrato del job de retención de `identidad.evento_auditoria` contra Postgres real (ADR-0017 D8,
 * ADR-0014 D10 categoría 2): filas fuera de la ventana de 12 meses se purgan, filas dentro se conservan.
 * Cada test siembra su propio `id` fresco y filtra por él (`docs/arquitectura/testing-de-modulos.md`).
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
        val UN_MES: Duration = Duration.ofDays(30)
        val ONCE_MESES: Duration = Duration.ofDays(11 * 30L)
        val TRECE_MESES: Duration = Duration.ofDays(13 * 30L)
    }
}
