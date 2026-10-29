package com.runcriticon.planificacion.application.listeners

import com.runcriticon.clubtaxonomia.api.events.EntrenadorAsignadoAGrupo
import com.runcriticon.clubtaxonomia.api.events.EntrenadorEliminadoDeGrupo
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.OutboxDeliveries
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Flujo completo de extremo a extremo: publicar en la transacción de un caso de uso, dejar que el outbox lo
 * entregue tras el commit, y comprobar el estado final de `planificacion.miembro_grupo`. Mismo patrón que
 * `PersonProjectionEventFlowIntegrationTest` de `club_taxonomia`.
 *
 * Los alumnos se cubren vía `MembresiaDeGrupoCambiada` (snapshot completo); los entrenadores siguen
 * llegando por `EntrenadorAsignadoAGrupo`/`EntrenadorEliminadoDeGrupo` (delta, sin cambios).
 */
class GroupMembersProjectionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `un alumno del snapshot aparece en la proyeccion con rol ALUMNO`() {
        val personId = UUID.randomUUID()
        val groupId = UUID.randomUUID()

        publish(membresiaCambiada(groupId, UUID.randomUUID(), listOf(personId)))

        awaitRow(groupId, personId)["rol"] shouldBe "ALUMNO"
    }

    @Test
    fun `un entrenador asignado a un grupo aparece en la proyeccion con rol ENTRENADOR`() {
        val personId = UUID.randomUUID()
        val groupId = UUID.randomUUID()

        // Instante fijo: Postgres guarda microsegundos y `Instant.now()` puede llevar más precisión.
        val event =
            entrenadorAsignado(
                personId,
                groupId,
                UUID.randomUUID(),
            ).copy(occurredAt = Instant.parse("2026-09-04T10:00:00Z"))

        publish(event)

        val row = awaitRow(groupId, personId)
        row["rol"] shouldBe "ENTRENADOR"
        row["last_processed_event_id"] shouldBe event.eventId
        (row["last_processed_event_ts"] as Timestamp).toInstant() shouldBe event.occurredAt
    }

    @Test
    fun `eliminar al entrenador del grupo borra su fila`() {
        val personId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        // Mismo clubId en los dos eventos: `remove` filtra por club_id, igual que un grupo real no cambia de club
        // entre una asignación y su baja.
        val clubId = UUID.randomUUID()
        publish(entrenadorAsignado(personId, groupId, clubId))
        awaitRow(groupId, personId)

        publish(
            EntrenadorEliminadoDeGrupo(
                eventId = UUID.randomUUID(),
                aggregateId = personId,
                occurredAt = Instant.now(),
                clubId = clubId,
                actorId = null,
                traceparent = null,
                groupId = groupId,
            ),
        )

        awaitAbsent(groupId, personId)
    }

    @Test
    fun `un snapshot vacio borra a los alumnos que ya no estan en el grupo`() {
        val personId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        publish(membresiaCambiada(groupId, clubId, listOf(personId)))
        awaitRow(groupId, personId)

        publish(membresiaCambiada(groupId, clubId, emptyList(), occurredAt = Instant.now().plusSeconds(1)))

        awaitAbsent(groupId, personId)
    }

    @Test
    fun `un nuevo snapshot no toca las filas de entrenador del mismo grupo`() {
        val alumno = UUID.randomUUID()
        val entrenador = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        publish(entrenadorAsignado(entrenador, groupId, clubId))
        val filaEntrenadorAntes = awaitRow(groupId, entrenador)

        publish(membresiaCambiada(groupId, clubId, listOf(alumno)))
        awaitRow(groupId, alumno)

        // `DELETE ... WHERE rol = 'ALUMNO'` del snapshot no toca la fila de entrenador, ni siquiera para
        // reescribir sus columnas de versión: sigue siendo exactamente la misma fila que antes del snapshot.
        readRow(groupId, entrenador) shouldBe filaEntrenadorAntes
    }

    @Test
    fun `un snapshot mas antiguo se descarta por la guarda de orden`() {
        val alumnoNuevo = UUID.randomUUID()
        val alumnoViejo = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        val ahora = Instant.now()

        val reciente = membresiaCambiada(groupId, clubId, listOf(alumnoNuevo), occurredAt = ahora)
        publish(reciente)
        awaitRow(groupId, alumnoNuevo)

        // Mismo grupo, snapshot con `occurredAt` anterior: la guarda de orden vive por grupo
        // (`miembro_grupo_version`), no por fila, así que este snapshot "viejo" no debe pisar al ya aplicado.
        val viejo = membresiaCambiada(groupId, clubId, listOf(alumnoViejo), occurredAt = ahora.minusSeconds(60))
        publish(viejo)

        OutboxDeliveries(jdbc).awaitCompleted(GroupMembersProjectionListener::class, viejo.eventId)
        val row = readRow(groupId, alumnoNuevo).shouldNotBeNull()
        row["last_processed_event_id"] shouldBe reciente.eventId
        readRow(groupId, alumnoViejo) shouldBe null
    }

    @Test
    fun `reentregar el mismo snapshot no duplica ni falla`() {
        val personId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val event = membresiaCambiada(groupId, UUID.randomUUID(), listOf(personId))
        publish(event)
        awaitRow(groupId, personId)

        // Misma instancia, mismo eventId: es lo que hace un reintento del outbox.
        publish(event)

        OutboxDeliveries(jdbc).awaitCompleted(GroupMembersProjectionListener::class, event.eventId, times = 2)
        countRows(groupId, personId) shouldBe 1
    }

    @Test
    fun `reentregar el mismo evento de entrenador no duplica la fila`() {
        val personId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val event = entrenadorAsignado(personId, groupId, UUID.randomUUID())
        publish(event)
        awaitRow(groupId, personId)

        publish(event)

        OutboxDeliveries(jdbc).awaitCompleted(GroupMembersProjectionListener::class, event.eventId, times = 2)
        countRows(groupId, personId) shouldBe 1
    }

    /**
     * El desorden que la guarda de orden promete cubrir: el outbox entrega la baja antes que la asignación que la
     * precedió. El entrenador ya no lleva el grupo y no debe volver a contar como su entrenador.
     */
    @Test
    fun `una asignacion de entrenador que llega despues de su baja no resucita la fila`() {
        val personId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        val deliveries = OutboxDeliveries(jdbc)
        val removed =
            EntrenadorEliminadoDeGrupo(
                eventId = UUID.randomUUID(),
                aggregateId = personId,
                occurredAt = Instant.parse("2026-09-04T11:00:00Z"),
                clubId = clubId,
                actorId = null,
                traceparent = null,
                groupId = groupId,
            )
        publish(removed)
        deliveries.awaitCompleted(GroupMembersProjectionListener::class, removed.eventId)

        val stale =
            entrenadorAsignado(
                personId,
                groupId,
                clubId,
            ).copy(occurredAt = Instant.parse("2026-09-04T10:00:00Z"))
        publish(stale)
        deliveries.awaitCompleted(GroupMembersProjectionListener::class, stale.eventId)

        readRow(groupId, personId) shouldBe null
    }

    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun membresiaCambiada(
        groupId: UUID,
        clubId: UUID,
        alumnos: List<UUID>,
        occurredAt: Instant = Instant.now(),
    ) = MembresiaDeGrupoCambiada(
        eventId = UUID.randomUUID(),
        aggregateId = groupId,
        occurredAt = occurredAt,
        clubId = clubId,
        actorId = null,
        traceparent = null,
        alumnos = alumnos,
    )

    private fun entrenadorAsignado(
        personId: UUID,
        groupId: UUID,
        clubId: UUID,
    ) = EntrenadorAsignadoAGrupo(
        eventId = UUID.randomUUID(),
        aggregateId = personId,
        occurredAt = Instant.now(),
        clubId = clubId,
        actorId = null,
        traceparent = null,
        groupId = groupId,
    )

    private fun awaitRow(
        groupId: UUID,
        personId: UUID,
    ): Map<String, Any?> = await("no se proyectó la fila ($groupId, $personId)") { readRow(groupId, personId) }

    private fun awaitAbsent(
        groupId: UUID,
        personId: UUID,
    ) {
        await("la fila ($groupId, $personId) no se borró") { if (readRow(groupId, personId) == null) Unit else null }
    }

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

    private fun readRow(
        groupId: UUID,
        personId: UUID,
    ): Map<String, Any?>? =
        jdbc
            .queryForList(
                "SELECT * FROM planificacion.miembro_grupo WHERE grupo_id = ? AND persona_id = ?",
                groupId,
                personId,
            ).firstOrNull()

    private fun countRows(
        groupId: UUID,
        personId: UUID,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM planificacion.miembro_grupo WHERE grupo_id = ? AND persona_id = ?",
            Int::class.java,
            groupId,
            personId,
        ) ?: 0

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
    }
}
