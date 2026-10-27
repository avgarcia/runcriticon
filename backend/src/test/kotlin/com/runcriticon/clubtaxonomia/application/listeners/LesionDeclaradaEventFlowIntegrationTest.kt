package com.runcriticon.clubtaxonomia.application.listeners

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.shared.api.events.LesionDeclarada
import com.runcriticon.shared.tenancy.ClubId
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
 * [LesionDeclaradaListener] de punta a punta: evento real por el outbox, sin `SecurityContext` (como en producción,
 * donde el listener corre asíncrono tras el commit), contra los repositorios reales con `@AuthScope`.
 */
class LesionDeclaradaEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())

    @Test
    fun `avisar de lesion asigna el tag estado = lesion al alumno`() {
        val estado = sembrarEje("estado")
        val lesion = sembrarValor(estado, "lesión")
        val alumno = sembrarAlumno()

        transactions.executeWithoutResult {
            events.publishEvent(
                LesionDeclarada(
                    eventId = UUID.randomUUID(),
                    aggregateId = alumno,
                    occurredAt = Instant.now(),
                    clubId = club.value,
                    actorId = alumno,
                    traceparent = null,
                ),
            )
        }

        awaitTag(alumno, lesion) shouldBe true
    }

    private fun awaitTag(
        alumno: UUID,
        valor: UUID,
    ): Boolean {
        val deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadline) {
            val n =
                jdbc.queryForObject(
                    "SELECT count(*) FROM club_taxonomia.alumno_tag WHERE alumno_id = ? AND tag_value_id = ?",
                    Int::class.java,
                    alumno,
                    valor,
                )
            if (n == 1) return true
            Thread.sleep(25)
        }
        return false
    }

    private fun sembrarAlumno(): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            """
            INSERT INTO club_taxonomia.persona
                (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, 'ALUMNO', 'ACTIVO', ?, now())
            """.trimIndent(),
            id,
            club.value,
            "Alumno $id",
            "alumno-$id@club.test",
            UuidCreator.getTimeOrderedEpoch(),
        )
        return id
    }

    private fun sembrarEje(nombre: String): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update("INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)", id, club.value, nombre)
        return id
    }

    private fun sembrarValor(
        keyId: UUID,
        nombre: String,
    ): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre) VALUES (?, ?, ?, ?)",
            id,
            keyId,
            club.value,
            nombre,
        )
        return id
    }
}
