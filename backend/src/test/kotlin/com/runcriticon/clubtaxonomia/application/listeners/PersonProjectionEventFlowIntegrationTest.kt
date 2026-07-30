package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.identidad.api.events.AlumnoActivado
import com.runcriticon.identidad.api.events.AlumnoInvitado
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
 * Flujo completo de extremo a extremo: publicar el evento en la transacción de un caso de uso, dejar que el outbox lo
 * entregue tras el commit, y comprobar el estado final de la proyección. Es el único test que ejercita el camino real —
 * `@ApplicationModuleListener`, transacción propia del listener, tabla de idempotencia — y no una llamada directa.
 *
 * Cada caso usa su propia persona y su propio club, sin borrar la tabla: la entrega es asíncrona, y limpiar en un
 * `@BeforeEach` competiría con las entregas del caso anterior.
 */
class PersonProjectionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `un alumno invitado aparece en la proyeccion del club`() {
        val personId = UUID.randomUUID()
        val clubId = UUID.randomUUID()

        publish(alumnoInvitado(personId, clubId))

        val row = awaitPerson(personId)
        row["club_id"] shouldBe clubId
        row["nombre"] shouldBe "Beto Ruiz"
        row["email"] shouldBe "beto@club.test"
        row["rol"] shouldBe "ALUMNO"
        row["estado"] shouldBe "INVITADO"
    }

    @Test
    fun `un entrenador invitado aparece con su rol`() {
        val personId = UUID.randomUUID()

        publish(
            EntrenadorInvitado(
                eventId = UUID.randomUUID(),
                aggregateId = personId,
                occurredAt = Instant.now(),
                clubId = UUID.randomUUID(),
                actorId = null,
                traceparent = null,
                name = "Ana Soto",
                email = "ana@club.test",
            ),
        )

        awaitPerson(personId)["rol"] shouldBe "ENTRENADOR"
    }

    @Test
    fun `activar la cuenta deja la persona activa en la proyeccion`() {
        val personId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        publish(alumnoInvitado(personId, clubId))
        awaitPerson(personId)

        publish(
            AlumnoActivado(
                eventId = UUID.randomUUID(),
                aggregateId = personId,
                occurredAt = Instant.now(),
                clubId = clubId,
                actorId = null,
                traceparent = null,
                name = "Beto Ruiz",
                email = "beto@club.test",
            ),
        )

        awaitState(personId, "ACTIVO")
    }

    @Test
    fun `reentregar el mismo evento no duplica ni corrompe la fila`() {
        val personId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        val invitation = alumnoInvitado(personId, clubId)
        publish(invitation)
        awaitPerson(personId)
        publish(
            AlumnoActivado(
                eventId = UUID.randomUUID(),
                aggregateId = personId,
                occurredAt = Instant.now(),
                clubId = clubId,
                actorId = null,
                traceparent = null,
                name = "Beto Ruiz",
                email = "beto@club.test",
            ),
        )
        awaitState(personId, "ACTIVO")

        // Misma instancia, mismo eventId: es lo que hace un reintento del outbox. La invitación es además el evento
        // *anterior*, así que si el tracker fallara, la guarda de orden todavía impediría volver a INVITADO.
        publish(invitation)

        Thread.sleep(SETTLE_MILLIS)
        countPersons(personId) shouldBe 1
        readPerson(personId)!!["estado"] shouldBe "ACTIVO"
        countProcessed(invitation.eventId) shouldBe 1
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

    private fun awaitPerson(personId: UUID): Map<String, Any?> =
        await("no se proyectó la persona $personId") { readPerson(personId) }

    private fun awaitState(
        personId: UUID,
        estado: String,
    ): Map<String, Any?> =
        await("la persona $personId no pasó a estado $estado") {
            readPerson(personId)?.takeIf { it["estado"] == estado }
        }

    /** Sondeo corto hasta que la entrega asíncrona del outbox se materialice, con el patrón del resto de tests. */
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

    private fun readPerson(personId: UUID): Map<String, Any?>? =
        jdbc
            .queryForList("SELECT * FROM club_taxonomia.persona WHERE id = ?", personId)
            .firstOrNull()

    private fun countPersons(personId: UUID): Int =
        jdbc.queryForObject("SELECT count(*) FROM club_taxonomia.persona WHERE id = ?", Int::class.java, personId) ?: 0

    private fun countProcessed(eventId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM club_taxonomia.evento_procesado WHERE event_id = ?",
            Int::class.java,
            eventId,
        ) ?: 0

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L

        /** Margen para que una reentrega que *no debe* cambiar nada haya tenido tiempo de procesarse. */
        const val SETTLE_MILLIS = 500L
    }
}
