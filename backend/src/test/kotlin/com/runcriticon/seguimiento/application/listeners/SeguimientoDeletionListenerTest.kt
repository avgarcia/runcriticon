package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ErasedRows
import com.runcriticon.seguimiento.application.ports.outbound.persistence.SeguimientoErasure
import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.observability.MdcRestorerForEvents
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.slf4j.MDC
import java.time.Instant
import java.util.UUID

/**
 * Comportamiento del listener de supresión aislado de la base de datos. Mismo patrón que
 * `ResolvedPlanProjectionListenerTest`, comparte sus dobles [InMemoryProcessedEventTracker]/[ConstantUserIdHasher].
 */
class SeguimientoDeletionListenerTest :
    FunSpec({
        lateinit var erasure: RecordingSeguimientoErasure
        lateinit var processedEvents: InMemoryProcessedEventTracker
        lateinit var listener: SeguimientoDeletionListener

        beforeEach {
            erasure = RecordingSeguimientoErasure()
            processedEvents = InMemoryProcessedEventTracker()
            listener =
                SeguimientoDeletionListener(
                    erasure = erasure,
                    processedEvents = processedEvents,
                    mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher),
                )
        }

        afterEach { MDC.clear() }

        test("un alumno eliminado borra su proyeccion") {
            val studentId = UUID.randomUUID()

            listener.on(alumnoEliminado(studentId = studentId))

            erasure.erased.single() shouldBe StudentId.of(studentId)
        }

        test("reentregar el mismo evento no vuelve a borrar") {
            val event = alumnoEliminado()

            listener.on(event)
            listener.on(event)

            erasure.erased shouldHaveSize 1
        }

        test("al terminar el listener el MDC queda limpio") {
            listener.on(alumnoEliminado())

            MDC.get("module").shouldBeNull()
        }

        test("si el borrado falla el MDC igualmente queda limpio") {
            erasure.failure = IllegalStateException("no se pudo borrar")

            shouldThrow<IllegalStateException> { listener.on(alumnoEliminado()) }

            MDC.get("module").shouldBeNull()
        }

        // LAL-116: EntrenadorEliminado.

        test("un entrenador eliminado borra sus filas de grupo_entrenador") {
            val coachId = UUID.randomUUID()

            listener.on(entrenadorEliminado(coachId = coachId))

            erasure.erasedCoaches.single() shouldBe CoachId.of(coachId)
        }

        test("reentregar el mismo evento de entrenador no vuelve a borrar") {
            val event = entrenadorEliminado()

            listener.on(event)
            listener.on(event)

            erasure.erasedCoaches shouldHaveSize 1
        }
    })

private fun alumnoEliminado(
    studentId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
) = AlumnoEliminado(
    eventId = UUID.randomUUID(),
    aggregateId = studentId,
    occurredAt = Instant.parse("2026-08-13T10:00:00Z"),
    clubId = clubId,
    actorId = null,
    traceparent = null,
)

private fun entrenadorEliminado(
    coachId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
) = EntrenadorEliminado(
    eventId = UUID.randomUUID(),
    aggregateId = coachId,
    occurredAt = Instant.parse("2026-08-13T10:00:00Z"),
    clubId = clubId,
    actorId = null,
    traceparent = null,
)

private class RecordingSeguimientoErasure : SeguimientoErasure {
    val erased = mutableListOf<StudentId>()
    val erasedCoaches = mutableListOf<CoachId>()
    var failure: RuntimeException? = null

    override fun erase(studentId: StudentId): ErasedRows {
        failure?.let { throw it }
        erased += studentId
        return ErasedRows(resolvedSessions = 0)
    }

    override fun eraseCoach(coachId: CoachId): Int {
        failure?.let { throw it }
        erasedCoaches += coachId
        return 1
    }
}
