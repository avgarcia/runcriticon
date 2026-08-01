package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonProjection
import com.runcriticon.clubtaxonomia.domain.person.Person
import com.runcriticon.clubtaxonomia.domain.person.PersonRole
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.identidad.api.events.AlumnoActivado
import com.runcriticon.identidad.api.events.AlumnoInvitado
import com.runcriticon.identidad.api.events.EntrenadorActivado
import com.runcriticon.identidad.api.events.EntrenadorInvitado
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.observability.UserIdHasher
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.slf4j.MDC
import java.time.Instant
import java.util.UUID

/**
 * Comportamiento del listener aislado de la base de datos: qué escribe por cada evento, que no escribe cuando el evento
 * ya estaba procesado, y que el MDC se restaura y se limpia siempre.
 *
 * La **guarda de orden** no se prueba aquí: vive en el `WHERE` del upsert, así que solo un Postgres real puede
 * verificarla (`PersonProjectionIntegrationTest`). Este doble la simula devolviendo `false`, que es lo único que el
 * listener necesita saber.
 */
class PersonProjectionListenerTest :
    FunSpec({
        lateinit var projection: RecordingPersonProjection
        lateinit var processedEvents: InMemoryProcessedEventTracker
        lateinit var listener: PersonProjectionListener

        beforeEach {
            projection = RecordingPersonProjection()
            processedEvents = InMemoryProcessedEventTracker()
            listener =
                PersonProjectionListener(
                    projection = projection,
                    processedEvents = processedEvents,
                    mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher),
                )
        }

        afterEach { MDC.clear() }

        test("un alumno invitado se proyecta como alumno en estado invitado") {
            val event = alumnoInvitado(name = "Beto Ruiz", email = "beto@club.test")

            listener.on(event)

            projection.written shouldHaveSize 1
            val (person, eventId, occurredAt) = projection.written.single()
            person.id.value shouldBe event.aggregateId
            person.clubId.value shouldBe event.clubId
            person.name shouldBe "Beto Ruiz"
            person.email shouldBe "beto@club.test"
            person.role shouldBe PersonRole.ALUMNO
            person.status shouldBe PersonStatus.INVITADO
            eventId shouldBe event.eventId
            occurredAt shouldBe event.occurredAt
        }

        test("un alumno activado se proyecta en estado activo") {
            listener.on(
                AlumnoActivado(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = Instant.parse("2026-07-30T10:00:00Z"),
                    clubId = UUID.randomUUID(),
                    actorId = null,
                    traceparent = null,
                    name = "Beto Ruiz",
                    email = "beto@club.test",
                ),
            )

            projection.written
                .single()
                .person.status shouldBe PersonStatus.ACTIVO
            projection.written
                .single()
                .person.role shouldBe PersonRole.ALUMNO
        }

        test("un entrenador invitado se proyecta con rol entrenador") {
            listener.on(
                EntrenadorInvitado(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = Instant.parse("2026-07-30T10:00:00Z"),
                    clubId = UUID.randomUUID(),
                    actorId = null,
                    traceparent = null,
                    name = "Ana Soto",
                    email = "ana@club.test",
                ),
            )

            projection.written
                .single()
                .person.role shouldBe PersonRole.ENTRENADOR
            projection.written
                .single()
                .person.status shouldBe PersonStatus.INVITADO
        }

        test("un entrenador activado se proyecta con rol entrenador en estado activo") {
            listener.on(
                EntrenadorActivado(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = Instant.parse("2026-07-30T10:00:00Z"),
                    clubId = UUID.randomUUID(),
                    actorId = null,
                    traceparent = null,
                    name = "Ana Soto",
                    email = "ana@club.test",
                ),
            )

            projection.written
                .single()
                .person.role shouldBe PersonRole.ENTRENADOR
            projection.written
                .single()
                .person.status shouldBe PersonStatus.ACTIVO
        }

        test("reentregar el mismo evento no vuelve a escribir en la proyeccion") {
            val event = alumnoInvitado()

            listener.on(event)
            listener.on(event)

            projection.written shouldHaveSize 1
        }

        test("dos eventos distintos de la misma persona se procesan los dos") {
            val personId = UUID.randomUUID()
            val clubId = UUID.randomUUID()

            listener.on(alumnoInvitado(personId = personId, clubId = clubId))
            listener.on(
                AlumnoActivado(
                    eventId = UUID.randomUUID(),
                    aggregateId = personId,
                    occurredAt = Instant.parse("2026-07-30T11:00:00Z"),
                    clubId = clubId,
                    actorId = null,
                    traceparent = null,
                    name = "Beto Ruiz",
                    email = "beto@club.test",
                ),
            )

            projection.written shouldHaveSize 2
        }

        test("la escritura descartada por la guarda de orden no rompe el listener") {
            projection.acceptWrites = false

            listener.on(alumnoInvitado())

            projection.written.shouldBeEmpty()
        }

        test("durante el procesado el MDC lleva el modulo que consume y el trace del evento") {
            val actorId = UUID.randomUUID()
            val clubId = UUID.randomUUID()

            listener.on(
                alumnoInvitado(clubId = clubId).copy(
                    actorId = actorId,
                    traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                ),
            )

            projection.mdcSnapshot["module"] shouldBe "club_taxonomia"
            projection.mdcSnapshot["trace_id"] shouldBe "0af7651916cd43dd8448eb211c80319c"
            projection.mdcSnapshot["club_id"] shouldBe clubId.toString()
            projection.mdcSnapshot["user_id_hash"] shouldBe ConstantUserIdHasher.HASH
        }

        test("al terminar el listener el MDC queda limpio") {
            listener.on(alumnoInvitado())

            MDC.get("module").shouldBeNull()
            MDC.get("club_id").shouldBeNull()
        }

        test("si la escritura falla el MDC igualmente queda limpio") {
            projection.failure = IllegalStateException("no se pudo escribir la proyección")

            shouldThrow<IllegalStateException> { listener.on(alumnoInvitado()) }

            MDC.get("module").shouldBeNull()
        }
    })

private fun alumnoInvitado(
    personId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
    name: String = "Beto Ruiz",
    email: String = "beto@club.test",
) = AlumnoInvitado(
    eventId = UUID.randomUUID(),
    aggregateId = personId,
    occurredAt = Instant.parse("2026-07-30T10:00:00Z"),
    clubId = clubId,
    actorId = null,
    traceparent = null,
    name = name,
    email = email,
)

/** Escritura registrada en la proyección, con el MDC vigente en ese momento. */
private data class Write(
    val person: Person,
    val eventId: UUID,
    val occurredAt: Instant,
)

/**
 * Doble de [PersonProjection] que apunta lo que se le pide escribir. [acceptWrites] simula el descarte por la guarda de
 * orden y [failure] un fallo de la base de datos.
 */
private class RecordingPersonProjection : PersonProjection {
    val written = mutableListOf<Write>()
    val mdcSnapshot = mutableMapOf<String, String>()
    var acceptWrites = true
    var failure: RuntimeException? = null

    override fun upsert(
        person: Person,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean {
        MDC.getCopyOfContextMap()?.let(mdcSnapshot::putAll)
        failure?.let { throw it }
        if (!acceptWrites) return false
        written += Write(person, eventId, occurredAt)
        return true
    }

    override fun lagSeconds(): Long = 0L
}

/**
 * Doble de [ProcessedEventTracker] con la misma semántica que la clave primaria de `evento_procesado`. `internal` y no
 * `private`: lo comparten los dos listeners del módulo, y dos declaraciones privadas con el mismo nombre en el mismo
 * paquete colisionarían al compilar.
 */
internal class InMemoryProcessedEventTracker : ProcessedEventTracker {
    private val processed = mutableSetOf<Pair<String, UUID>>()

    override fun markIfNew(
        listener: String,
        eventId: UUID,
    ): Boolean = processed.add(listener to eventId)
}

internal object ConstantUserIdHasher : UserIdHasher {
    const val HASH = "hash-del-actor"

    override fun hash(userId: UUID): String = HASH
}
