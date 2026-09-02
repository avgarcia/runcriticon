package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.seguimiento.api.events.MarcaActualizada
import com.runcriticon.seguimiento.api.events.MarcaRetirada
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.slf4j.MDC
import java.time.Instant
import java.util.UUID

/**
 * Comportamiento del listener aislado de la base de datos (LAL-32): qué le pide a la proyección por cada
 * evento, la idempotencia frente a reentregas, y el detalle central del diseño — **vuelve a leer la marca
 * actual, no confía en el payload del evento** (ver el KDoc de la clase bajo prueba). El `UPDATE` real y su
 * alcance por `(club_id, alumno_id, distancia)` solo los puede verificar un Postgres real
 * (`MarkPaceRecalculationEventFlowIntegrationTest`).
 */
class MarkPaceRecalculationListenerTest :
    FunSpec({
        lateinit var projection: RecordingRecalculation
        lateinit var marks: InMemoryStudentMarkLookup
        lateinit var processedEvents: InMemoryProcessedEventTracker
        lateinit var listener: MarkPaceRecalculationListener

        beforeEach {
            projection = RecordingRecalculation()
            marks = InMemoryStudentMarkLookup()
            processedEvents = InMemoryProcessedEventTracker()
            listener =
                MarkPaceRecalculationListener(
                    projection = projection,
                    marks = marks,
                    processedEvents = processedEvents,
                    mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher),
                )
        }

        afterEach { MDC.clear() }

        test("MarcaActualizada relee la marca actual y recalcula con su pace, ignorando el tiempo del evento") {
            val alumnoId = UUID.randomUUID()
            val clubId = UUID.randomUUID()
            // La marca "actual" en la BD es distinta de la que trae el evento — a propósito: el listener
            // debe usar esta, no `event.tiempoSegundos` (ver KDoc de la clase).
            marks.put(StudentId.of(alumnoId), mark(RaceDistance.TEN_K, timeSeconds = 2_400))
            val event =
                marcaActualizada(alumnoId = alumnoId, clubId = clubId, distancia = "10K", tiempoSegundos = 9_999)

            listener.on(event)

            val call = projection.calls.single()
            call.clubId shouldBe ClubId.of(clubId)
            call.studentId shouldBe StudentId.of(alumnoId)
            call.distance shouldBe RaceDistance.TEN_K
            call.markPaceSecondsPerKm shouldBe 240
        }

        test("MarcaActualizada sin marca en la BD (reprocesada tras un borrado posterior) recalcula a null") {
            val event = marcaActualizada(distancia = "5K")

            listener.on(event)

            projection.calls
                .single()
                .markPaceSecondsPerKm
                .shouldBeNull()
        }

        test("MarcaRetirada recalcula con markPaceSecondsPerKm null") {
            val alumnoId = UUID.randomUUID()
            // Aunque hubiera quedado un residuo, MarcaRetirada implica que ya no hay marca: el listener lee
            // igualmente el store (aquí vacío, como en el caso real tras el DELETE).
            val event = marcaRetirada(alumnoId = alumnoId, distancia = "42K")

            listener.on(event)

            val call = projection.calls.single()
            call.studentId shouldBe StudentId.of(alumnoId)
            call.distance shouldBe RaceDistance.MARATHON
            call.markPaceSecondsPerKm.shouldBeNull()
        }

        test("un MarcaActualizada ya procesado no vuelve a recalcular") {
            val event = marcaActualizada()
            listener.on(event)

            listener.on(event)

            projection.calls shouldHaveSize 1
        }

        test("un MarcaRetirada ya procesado no vuelve a recalcular") {
            val event = marcaRetirada()
            listener.on(event)

            listener.on(event)

            projection.calls shouldHaveSize 1
        }

        test("durante el procesado el MDC lleva el modulo seguimiento y el trace del evento") {
            val actorId = UUID.randomUUID()
            val clubId = UUID.randomUUID()

            listener.on(
                marcaActualizada(clubId = clubId).copy(
                    actorId = actorId,
                    traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                ),
            )

            projection.mdcSnapshot["module"] shouldBe "seguimiento"
            projection.mdcSnapshot["trace_id"] shouldBe "0af7651916cd43dd8448eb211c80319c"
            projection.mdcSnapshot["club_id"] shouldBe clubId.toString()
        }

        test("al terminar el listener el MDC queda limpio, tanto para MarcaActualizada como para MarcaRetirada") {
            listener.on(marcaActualizada())
            MDC.get("module").shouldBeNull()

            listener.on(marcaRetirada())
            MDC.get("module").shouldBeNull()
        }
    })

private fun mark(
    distance: RaceDistance,
    timeSeconds: Int,
) = StudentMark(distance, timeSeconds, modifiedAt = Instant.parse("2026-08-01T00:00:00Z"))

private fun marcaActualizada(
    alumnoId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
    distancia: String = "10K",
    tiempoSegundos: Int = 2_400,
) = MarcaActualizada(
    eventId = UUID.randomUUID(),
    aggregateId = alumnoId,
    occurredAt = Instant.parse("2026-08-29T07:30:00Z"),
    clubId = clubId,
    actorId = null,
    traceparent = null,
    distancia = distancia,
    tiempoSegundos = tiempoSegundos,
)

private fun marcaRetirada(
    alumnoId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
    distancia: String = "10K",
) = MarcaRetirada(
    eventId = UUID.randomUUID(),
    aggregateId = alumnoId,
    occurredAt = Instant.parse("2026-08-29T07:31:00Z"),
    clubId = clubId,
    actorId = null,
    traceparent = null,
    distancia = distancia,
)

private data class RecalculationCall(
    val clubId: ClubId,
    val studentId: StudentId,
    val distance: RaceDistance,
    val markPaceSecondsPerKm: Int?,
)

/** Doble de [ResolvedPlanProjection] que solo implementa `recalculateRelativePaces` — el resto no lo usa este
 * listener. */
private class RecordingRecalculation : ResolvedPlanProjection {
    val calls = mutableListOf<RecalculationCall>()
    val mdcSnapshot = mutableMapOf<String, String>()

    override fun replacePlan(
        clubId: ClubId,
        planId: PlanId,
        sessionsByStudent: Map<StudentId, List<ResolvedSession>>,
        eventId: UUID,
        occurredAt: Instant,
    ) = error("no usado en MarkPaceRecalculationListenerTest")

    override fun lagSeconds(): Long = error("no usado en MarkPaceRecalculationListenerTest")

    override fun writePersonalizedSession(
        clubId: ClubId,
        studentId: StudentId,
        session: ResolvedSession,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean = error("no usado en MarkPaceRecalculationListenerTest")

    override fun recalculateRelativePaces(
        clubId: ClubId,
        studentId: StudentId,
        distance: RaceDistance,
        markPaceSecondsPerKm: Int?,
    ): Int {
        MDC.getCopyOfContextMap()?.let(mdcSnapshot::putAll)
        calls += RecalculationCall(clubId, studentId, distance, markPaceSecondsPerKm)
        return 1
    }
}
