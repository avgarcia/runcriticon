package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.AlumnoInvitado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.identidad.api.events.EntrenadorInvitado
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
 * Supresión de extremo a extremo por el camino real: publicar en la transacción de un caso de uso, dejar que el outbox
 * entregue tras el commit y comprobar el estado final.
 *
 * Cada caso usa su propia persona y su propio club, sin limpiar entre tests: la entrega es asíncrona y un `@BeforeEach`
 * que borrara competiría con las entregas del caso anterior.
 */
class StudentDeletionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `la baja de un alumno borra su fila de la proyeccion`() {
        val personId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        publish(alumnoInvitado(personId, clubId))
        await("no se proyectó la persona") { existePersona(personId) }

        publish(alumnoEliminado(personId, clubId))

        await("la persona no se borró") { !existePersona(personId) }
        contarLapidas(personId) shouldBe 1
    }

    @Test
    fun `la baja de un entrenador tambien borra su fila`() {
        val personId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        publish(
            EntrenadorInvitado(
                eventId = UUID.randomUUID(),
                aggregateId = personId,
                occurredAt = Instant.now(),
                clubId = clubId,
                actorId = null,
                traceparent = null,
                name = "Ana Soto",
                email = "ana@club.test",
            ),
        )
        await("no se proyectó el entrenador") { existePersona(personId) }

        publish(
            EntrenadorEliminado(
                eventId = UUID.randomUUID(),
                aggregateId = personId,
                occurredAt = Instant.now(),
                clubId = clubId,
                actorId = null,
                traceparent = null,
            ),
        )

        await("el entrenador no se borró") { !existePersona(personId) }
    }

    /**
     * **El caso que cierra el agujero de resurrección.** El alta llega *después* de la baja y con `occurredAt`
     * posterior, así que la guarda de orden no puede explicarlo: si la fila reapareciera, sería la lápida la que no
     * está haciendo su trabajo. Es el escenario de un evento que agotó sus reintentos, cayó a la DLQ y alguien la
     * republicó cuando la persona ya había ejercido su derecho de supresión.
     */
    @Test
    fun `un alta posterior a la baja no resucita a la persona`() {
        val personId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        publish(alumnoEliminado(personId, clubId))
        await("no se escribió la lápida") { contarLapidas(personId) == 1 }

        publish(
            AlumnoInvitado(
                eventId = UUID.randomUUID(),
                aggregateId = personId,
                occurredAt = Instant.now().plusSeconds(DIEZ_SEGUNDOS),
                clubId = clubId,
                actorId = null,
                traceparent = null,
                name = "Beto Ruiz",
                email = "beto@club.test",
            ),
        )

        Thread.sleep(SETTLE_MILLIS)
        existePersona(personId) shouldBe false
    }

    @Test
    fun `reentregar la misma baja no falla ni duplica la lapida`() {
        val personId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        publish(alumnoInvitado(personId, clubId))
        await("no se proyectó la persona") { existePersona(personId) }
        val baja = alumnoEliminado(personId, clubId)
        publish(baja)
        await("la persona no se borró") { !existePersona(personId) }

        publish(baja)

        Thread.sleep(SETTLE_MILLIS)
        contarLapidas(personId) shouldBe 1
        contarProcesados(baja.eventId) shouldBe 1
    }

    @Test
    fun `una baja de alguien nunca proyectado deja la lapida sin romper nada`() {
        val personId = UUID.randomUUID()

        publish(alumnoEliminado(personId, UUID.randomUUID()))

        await("no se escribió la lápida") { contarLapidas(personId) == 1 }
        existePersona(personId) shouldBe false
    }

    /** Publica dentro de una transacción: `@ApplicationModuleListener` solo entrega tras un commit. */
    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun alumnoInvitado(
        personId: UUID,
        clubId: UUID,
    ) = AlumnoInvitado(
        eventId = UUID.randomUUID(),
        aggregateId = personId,
        occurredAt = Instant.now(),
        clubId = clubId,
        actorId = null,
        traceparent = null,
        name = "Beto Ruiz",
        email = "beto@club.test",
    )

    private fun alumnoEliminado(
        personId: UUID,
        clubId: UUID,
    ) = AlumnoEliminado(
        eventId = UUID.randomUUID(),
        aggregateId = personId,
        occurredAt = Instant.now(),
        clubId = clubId,
        actorId = UUID.randomUUID(),
        traceparent = null,
    )

    private fun await(
        failure: String,
        probe: () -> Boolean,
    ) {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(DEADLINE_SECONDS).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            if (probe()) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("$failure en $DEADLINE_SECONDS s")
    }

    private fun existePersona(personId: UUID): Boolean =
        (
            jdbc.queryForObject(
                "SELECT count(*) FROM club_taxonomia.persona WHERE id = ?",
                Int::class.java,
                personId,
            ) ?: 0
        ) > 0

    private fun contarLapidas(personId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.persona_eliminada WHERE id = ?",
            Int::class.java,
            personId,
        ) ?: 0

    private fun contarProcesados(eventId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.evento_procesado WHERE event_id = ?",
            Int::class.java,
            eventId,
        ) ?: 0

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
        const val DIEZ_SEGUNDOS = 10L

        /** Margen para que una entrega que *no debe* cambiar nada haya tenido tiempo de procesarse. */
        const val SETTLE_MILLIS = 800L
    }
}
