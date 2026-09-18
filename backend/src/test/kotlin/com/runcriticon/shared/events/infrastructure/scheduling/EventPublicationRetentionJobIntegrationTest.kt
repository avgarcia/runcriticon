package com.runcriticon.shared.events.infrastructure.scheduling

import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Contrato del job de retención de `event_publication` contra Postgres real (ADR-0017 D6, D7). `event_publication`
 * es la tabla compartida del outbox, sin `club_id` ni dueño de módulo: el marcador `serialized_event = '{}'`
 * identifica las filas sintéticas de este test (mismo patrón que `PublishPlanIntegrationTest`), y el
 * `@AfterEach` las limpia para no contaminar el lag que miden otros tests del mismo contenedor compartido.
 */
class EventPublicationRetentionJobIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var job: EventPublicationRetentionJob

    @Autowired private lateinit var jdbc: JdbcTemplate

    @AfterEach
    fun limpiaLasFilasSinteticas() {
        jdbc.update("DELETE FROM event_publication WHERE serialized_event = '{}'")
    }

    @Test
    fun `purga completadas mas antiguas que la retencion y conserva las recientes`() {
        val vieja = UUID.randomUUID()
        val reciente = UUID.randomUUID()
        sembrarPublicacion(vieja, completada = true, antiguedad = TREINTA_Y_UN_DIAS)
        sembrarPublicacion(reciente, completada = true, antiguedad = UN_DIA)

        job.purge()

        contar(vieja) shouldBe 0
        contar(reciente) shouldBe 1
    }

    @Test
    fun `nunca purga una publicacion sin completar, por muy antigua que sea`() {
        val pendienteAntigua = UUID.randomUUID()
        sembrarPublicacion(pendienteAntigua, completada = false, antiguedad = CIEN_DIAS)

        job.purge()

        contar(pendienteAntigua) shouldBe 1
    }

    @Test
    fun `una completada justo dentro de la ventana de 30 dias no se purga`() {
        val id = UUID.randomUUID()
        sembrarPublicacion(id, completada = true, antiguedad = VEINTINUEVE_DIAS)

        job.purge()

        contar(id) shouldBe 1
    }

    private fun sembrarPublicacion(
        id: UUID,
        completada: Boolean,
        antiguedad: Duration,
    ) {
        val publicationDate = Timestamp.from(Instant.now().minus(antiguedad))
        val completionDate = if (completada) publicationDate else null
        jdbc.update(
            """
            INSERT INTO event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_date)
            VALUES (?, 'RetentionJobTest.listener', 'RetentionJobTest.Event', '{}', ?, ?)
            """.trimIndent(),
            id,
            publicationDate,
            completionDate,
        )
    }

    private fun contar(id: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication WHERE id = ?",
            Int::class.java,
            id,
        ) ?: 0

    private companion object {
        val UN_DIA: Duration = Duration.ofDays(1)
        val VEINTINUEVE_DIAS: Duration = Duration.ofDays(29)
        val TREINTA_Y_UN_DIAS: Duration = Duration.ofDays(31)
        val CIEN_DIAS: Duration = Duration.ofDays(100)
    }
}
