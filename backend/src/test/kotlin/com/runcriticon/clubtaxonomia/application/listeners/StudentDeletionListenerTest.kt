package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.AuditTrail
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.ErasedRows
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonErasure
import com.runcriticon.clubtaxonomia.domain.audit.AuditEntry
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.identidad.api.events.AdminEliminado
import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
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
 * Comportamiento del listener aislado de la base de datos: a quién manda borrar, cuándo no lo hace, y que el MDC se
 * restaura y se limpia siempre. Que el borrado alcance de verdad a las dos tablas y deje lápida lo prueban los tests
 * de integración, porque vive en SQL.
 */
class StudentDeletionListenerTest :
    FunSpec({
        lateinit var erasure: RecordingPersonErasure
        lateinit var auditTrail: RecordingAuditTrail
        lateinit var processedEvents: InMemoryProcessedEventTracker
        lateinit var listener: StudentDeletionListener

        beforeEach {
            erasure = RecordingPersonErasure()
            auditTrail = RecordingAuditTrail()
            processedEvents = InMemoryProcessedEventTracker()
            listener =
                StudentDeletionListener(
                    personErasure = erasure,
                    auditTrail = auditTrail,
                    processedEvents = processedEvents,
                    mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher),
                )
        }

        afterEach { MDC.clear() }

        test("la baja de un alumno borra lo que el club guarda de el") {
            val event = alumnoEliminado()

            listener.on(event)

            erasure.erased shouldHaveSize 1
            erasure.erased.single().value shouldBe event.aggregateId
        }

        test("la baja de un entrenador tambien se aplica") {
            val personId = UUID.randomUUID()

            listener.on(
                EntrenadorEliminado(
                    eventId = UUID.randomUUID(),
                    aggregateId = personId,
                    occurredAt = Instant.parse("2026-08-01T10:00:00Z"),
                    clubId = UUID.randomUUID(),
                    actorId = UUID.randomUUID(),
                    traceparent = null,
                ),
            )

            erasure.erased.single().value shouldBe personId
        }

        test("la baja de un admin no borra proyeccion pero si anonimiza su actor_id en auditoria") {
            val adminId = UUID.randomUUID()

            listener.on(
                AdminEliminado(
                    eventId = UUID.randomUUID(),
                    aggregateId = adminId,
                    occurredAt = Instant.parse("2026-08-01T10:00:00Z"),
                    clubId = UUID.randomUUID(),
                    actorId = UUID.randomUUID(),
                    traceparent = null,
                ),
            )

            // erase() se llama igual (mismo purge() compartido) pero es un no-op seguro contra la BD real: el admin
            // nunca tuvo fila de proyeccion. Aqui solo comprobamos que el listener no distingue el tipo de evento.
            erasure.erased.single().value shouldBe adminId
            auditTrail.anonymized.single() shouldBe adminId
        }

        test("reentregar la misma baja no vuelve a borrar") {
            val event = alumnoEliminado()

            listener.on(event)
            listener.on(event)

            erasure.erased shouldHaveSize 1
        }

        test("un evento ya procesado no llega al borrado") {
            val event = alumnoEliminado()
            processedEvents.markIfNew("StudentDeletionListener", event.eventId)

            listener.on(event)

            erasure.erased.shouldBeEmpty()
        }

        test("las bajas de dos personas distintas se aplican por separado") {
            listener.on(alumnoEliminado())
            listener.on(alumnoEliminado())

            erasure.erased shouldHaveSize 2
        }

        test("la baja tambien anonimiza los asientos de auditoria del suprimido") {
            val event = alumnoEliminado()

            listener.on(event)

            auditTrail.anonymized shouldHaveSize 1
            auditTrail.anonymized.single() shouldBe event.aggregateId
        }

        test("reentregar la misma baja no vuelve a anonimizar") {
            val event = alumnoEliminado()

            listener.on(event)
            listener.on(event)

            auditTrail.anonymized shouldHaveSize 1
        }

        test("durante el borrado el MDC lleva el modulo que consume y el trace del evento") {
            val clubId = UUID.randomUUID()

            listener.on(
                alumnoEliminado(clubId = clubId).copy(
                    traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                ),
            )

            erasure.mdcSnapshot["module"] shouldBe "club_taxonomia"
            erasure.mdcSnapshot["trace_id"] shouldBe "0af7651916cd43dd8448eb211c80319c"
            erasure.mdcSnapshot["club_id"] shouldBe clubId.toString()
        }

        test("al terminar el MDC queda limpio") {
            listener.on(alumnoEliminado())

            MDC.get("module").shouldBeNull()
        }

        test("si el borrado falla el MDC igualmente queda limpio") {
            erasure.failure = IllegalStateException("no se pudo borrar")

            shouldThrow<IllegalStateException> { listener.on(alumnoEliminado()) }

            MDC.get("module").shouldBeNull()
        }
    })

private fun alumnoEliminado(
    personId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
) = AlumnoEliminado(
    eventId = UUID.randomUUID(),
    aggregateId = personId,
    occurredAt = Instant.parse("2026-08-01T10:00:00Z"),
    clubId = clubId,
    actorId = UUID.randomUUID(),
    traceparent = null,
)

/** Doble de [PersonErasure] que apunta a quién se le pidió borrar, con el MDC vigente en ese momento. */
private class RecordingPersonErasure : PersonErasure {
    val erased = mutableListOf<PersonId>()
    val mdcSnapshot = mutableMapOf<String, String>()
    var failure: RuntimeException? = null

    override fun erase(personId: PersonId): ErasedRows {
        MDC.getCopyOfContextMap()?.let(mdcSnapshot::putAll)
        failure?.let { throw it }
        erased += personId
        return ErasedRows(projections = 1, tagAssignments = 2, groupOverrides = 1, groupCoachAssignments = 0)
    }
}

/** Doble de [AuditTrail] que apunta a quién se anonimizó. */
private class RecordingAuditTrail : AuditTrail {
    val anonymized = mutableListOf<UUID>()

    override fun record(
        clubId: ClubId,
        entry: AuditEntry,
    ) = error("no usado en este test")

    override fun anonymize(personId: UUID): Int {
        anonymized += personId
        return 1
    }
}

// Los dobles [InMemoryProcessedEventTracker] y [ConstantUserIdHasher] se comparten con PersonProjectionListenerTest:
// son idénticos y viven en el mismo paquete.
