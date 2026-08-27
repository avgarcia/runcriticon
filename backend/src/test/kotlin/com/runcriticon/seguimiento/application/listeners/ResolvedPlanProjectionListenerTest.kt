package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionVolume
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.observability.UserIdHasher
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.slf4j.MDC
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Comportamiento del listener aislado de la base de datos: qué le pide a la proyección por cada evento, que
 * no la toca cuando el evento ya estaba procesado, y que el MDC se restaura y se limpia siempre. Mismo patrón
 * que `PersonProjectionListenerTest` de `club_taxonomia`.
 *
 * La resolución del ritmo/volumen se prueba aquí (es lógica pura del mapeador); la **guarda de orden** y el
 * `ON CONFLICT` reales solo los puede verificar un Postgres real
 * (`ResolvedPlanProjectionEventFlowIntegrationTest`).
 */
class ResolvedPlanProjectionListenerTest :
    FunSpec({
        lateinit var projection: RecordingResolvedPlanProjection
        lateinit var processedEvents: InMemoryProcessedEventTracker
        lateinit var listener: ResolvedPlanProjectionListener

        beforeEach {
            projection = RecordingResolvedPlanProjection()
            processedEvents = InMemoryProcessedEventTracker()
            listener =
                ResolvedPlanProjectionListener(
                    projection = projection,
                    processedEvents = processedEvents,
                    mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher),
                )
        }

        afterEach { MDC.clear() }

        test("un PlanPublicado se proyecta con el snapshot de alumnos y las sesiones del evento") {
            val student1 = UUID.randomUUID()
            val student2 = UUID.randomUUID()
            val event =
                planPublicado(
                    students = listOf(student1, student2),
                    sessions = listOf(session(dia = "2026-08-17", tipo = "RODAJE")),
                )

            listener.on(event)

            val write = projection.written.single()
            write.planId shouldBe PlanId.of(event.aggregateId)
            write.clubId shouldBe ClubId.of(event.clubId)
            write.students shouldBe setOf(StudentId.of(student1), StudentId.of(student2))
            write.eventId shouldBe event.eventId
            write.occurredAt shouldBe event.occurredAt
        }

        test("una sesion con ritmo absoluto resuelve segundosPorKm y sin referencia") {
            val event =
                planPublicado(
                    sessions =
                        listOf(
                            session(
                                dia = "2026-08-17",
                                tipo = "TEMPO",
                                ritmoTipo = "ABSOLUTO",
                                ritmoSegundosPorKm = 240,
                            ),
                        ),
                )

            listener.on(event)

            val resolved =
                projection.written
                    .single()
                    .sessions
                    .single()
            resolved.pace?.secondsPerKm shouldBe 240
            resolved.pace?.referenceDistance.shouldBeNull()
            resolved.pace?.missingMark.shouldBeNull()
        }

        test("una sesion con ritmo relativo resuelve falta de marca, sin alumno con marca todavia (LAL-31)") {
            val event =
                planPublicado(
                    sessions =
                        listOf(
                            session(
                                dia = "2026-08-17",
                                tipo = "TEMPO",
                                ritmoTipo = "RELATIVO",
                                ritmoReferencia = "10K",
                            ),
                        ),
                )

            listener.on(event)

            val resolved =
                projection.written
                    .single()
                    .sessions
                    .single()
            resolved.pace?.secondsPerKm.shouldBeNull()
            resolved.pace?.missingMark shouldBe RaceDistance.TEN_K
        }

        test("una sesion de descanso sin volumen ni ritmo resuelve ambos a null") {
            val event = planPublicado(sessions = listOf(session(dia = "2026-08-17", tipo = "DESCANSO")))

            listener.on(event)

            val resolved =
                projection.written
                    .single()
                    .sessions
                    .single()
            resolved.volume.shouldBeNull()
            resolved.pace.shouldBeNull()
        }

        test("el volumen DISTANCIA del evento se resuelve como SessionVolume.Distance") {
            val event =
                planPublicado(
                    sessions =
                        listOf(
                            session(
                                dia = "2026-08-17",
                                tipo = "RODAJE",
                                volumenTipo = "DISTANCIA",
                                volumenMetros = 8000,
                            ),
                        ),
                )

            listener.on(event)

            projection.written
                .single()
                .sessions
                .single()
                .volume shouldBe SessionVolume.Distance(8000)
        }

        test("el volumen TIEMPO del evento se resuelve como SessionVolume.Duration") {
            val event =
                planPublicado(
                    sessions =
                        listOf(
                            session(dia = "2026-08-17", tipo = "RODAJE", volumenTipo = "TIEMPO", volumenMinutos = 45),
                        ),
                )

            listener.on(event)

            projection.written
                .single()
                .sessions
                .single()
                .volume shouldBe SessionVolume.Duration(45)
        }

        test("reentregar el mismo evento no vuelve a escribir en la proyeccion") {
            val event = planPublicado()

            listener.on(event)
            listener.on(event)

            projection.written shouldHaveSize 1
        }

        test("un snapshot vacio no rompe el listener") {
            val event = planPublicado(students = emptyList())

            listener.on(event)

            projection.written
                .single()
                .students
                .shouldBeEmpty()
        }

        test("durante el procesado el MDC lleva el modulo seguimiento y el trace del evento") {
            val actorId = UUID.randomUUID()
            val clubId = UUID.randomUUID()

            listener.on(
                planPublicado(clubId = clubId).copy(
                    actorId = actorId,
                    traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                ),
            )

            projection.mdcSnapshot["module"] shouldBe "seguimiento"
            projection.mdcSnapshot["trace_id"] shouldBe "0af7651916cd43dd8448eb211c80319c"
            projection.mdcSnapshot["club_id"] shouldBe clubId.toString()
            projection.mdcSnapshot["user_id_hash"] shouldBe ConstantUserIdHasher.HASH
        }

        test("al terminar el listener el MDC queda limpio") {
            listener.on(planPublicado())

            MDC.get("module").shouldBeNull()
            MDC.get("club_id").shouldBeNull()
        }

        test("si la escritura falla el MDC igualmente queda limpio") {
            projection.failure = IllegalStateException("no se pudo escribir la proyección")

            shouldThrow<IllegalStateException> { listener.on(planPublicado()) }

            MDC.get("module").shouldBeNull()
        }
    })

private fun session(
    dia: String,
    tipo: String,
    volumenTipo: String? = null,
    volumenMetros: Int? = null,
    volumenMinutos: Int? = null,
    ritmoTipo: String? = null,
    ritmoSegundosPorKm: Int? = null,
    ritmoReferencia: String? = null,
    ritmoDeltaSegundosPorKm: Int? = null,
    notas: String? = null,
) = PublishedSession(
    dia = LocalDate.parse(dia),
    tipo = tipo,
    volumenTipo = volumenTipo,
    volumenMetros = volumenMetros,
    volumenMinutos = volumenMinutos,
    ritmoTipo = ritmoTipo,
    ritmoSegundosPorKm = ritmoSegundosPorKm,
    ritmoReferencia = ritmoReferencia,
    ritmoDeltaSegundosPorKm = ritmoDeltaSegundosPorKm,
    notas = notas,
)

private fun planPublicado(
    planId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
    students: List<UUID> = listOf(UUID.randomUUID()),
    sessions: List<PublishedSession> = listOf(session(dia = "2026-08-17", tipo = "RODAJE")),
) = PlanPublicado(
    eventId = UUID.randomUUID(),
    aggregateId = planId,
    occurredAt = Instant.parse("2026-08-13T10:00:00Z"),
    clubId = clubId,
    actorId = null,
    traceparent = null,
    grupoId = UUID.randomUUID(),
    snapshotAlumnos = students,
    sesiones = sessions,
)

/** Escritura registrada en la proyección, con el MDC vigente en ese momento. */
private data class Write(
    val clubId: ClubId,
    val planId: PlanId,
    val students: Set<StudentId>,
    val sessions: List<ResolvedSession>,
    val eventId: UUID,
    val occurredAt: Instant,
)

/** Doble de [ResolvedPlanProjection] que apunta lo que se le pide escribir. */
private class RecordingResolvedPlanProjection : ResolvedPlanProjection {
    val written = mutableListOf<Write>()
    val mdcSnapshot = mutableMapOf<String, String>()
    var failure: RuntimeException? = null

    override fun replacePlan(
        clubId: ClubId,
        planId: PlanId,
        students: Set<StudentId>,
        sessions: List<ResolvedSession>,
        eventId: UUID,
        occurredAt: Instant,
    ) {
        MDC.getCopyOfContextMap()?.let(mdcSnapshot::putAll)
        failure?.let { throw it }
        written += Write(clubId, planId, students, sessions, eventId, occurredAt)
    }

    override fun lagSeconds(): Long = 0L

    override fun writePersonalizedSession(
        clubId: ClubId,
        studentId: StudentId,
        session: ResolvedSession,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean = error("No lo usa este listener — ver PersonalizationProjectionListenerTest (LAL-26)")
}

/**
 * Doble de [ProcessedEventTracker] con la misma semántica que la clave primaria de `evento_procesado`.
 * `internal` y no `private`: lo comparten los dos listeners del módulo (ver `SeguimientoDeletionListenerTest`),
 * y dos declaraciones privadas con el mismo nombre en el mismo paquete colisionarían al compilar.
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
