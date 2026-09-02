package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.application.ports.outbound.persistence.StudentMarkLookup
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ResolvedPace
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionVolume
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.observability.UserIdHasher
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
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
 * La resolución del ritmo/volumen se prueba aquí (es lógica pura del mapeador, incluida la resolución contra
 * la marca del alumno — LAL-32); la **guarda de orden** y el `ON CONFLICT` reales solo los puede verificar un
 * Postgres real (`ResolvedPlanProjectionEventFlowIntegrationTest`).
 */
class ResolvedPlanProjectionListenerTest :
    FunSpec({
        lateinit var projection: RecordingResolvedPlanProjection
        lateinit var marks: InMemoryStudentMarkLookup
        lateinit var processedEvents: InMemoryProcessedEventTracker
        lateinit var listener: ResolvedPlanProjectionListener

        beforeEach {
            projection = RecordingResolvedPlanProjection()
            marks = InMemoryStudentMarkLookup()
            processedEvents = InMemoryProcessedEventTracker()
            listener =
                ResolvedPlanProjectionListener(
                    projection = projection,
                    marks = marks,
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
            write.sessionsByStudent.keys shouldBe setOf(StudentId.of(student1), StudentId.of(student2))
            write.eventId shouldBe event.eventId
            write.occurredAt shouldBe event.occurredAt
        }

        test("una sesion con ritmo absoluto resuelve secondsPerKm igual para todos los alumnos") {
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

            val resolved = singleWrittenSession(projection)
            resolved.pace shouldBe ResolvedPace.Absolute(240)
        }

        test("una sesion con ritmo relativo y el alumno sin esa marca resuelve falta de marca") {
            val event =
                planPublicado(
                    sessions =
                        listOf(
                            session(
                                dia = "2026-08-17",
                                tipo = "TEMPO",
                                ritmoTipo = "RELATIVO",
                                ritmoReferencia = "10K",
                                ritmoDeltaSegundosPorKm = 10,
                            ),
                        ),
                )

            listener.on(event)

            val resolved = singleWrittenSession(projection)
            resolved.pace shouldBe
                ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = 10, secondsPerKm = null)
        }

        test("una sesion con ritmo relativo y el alumno con esa marca resuelve marca + delta (LAL-32)") {
            val student = UUID.randomUUID()
            marks.put(
                StudentId.of(student),
                markTenK(),
            )
            val event =
                planPublicado(
                    students = listOf(student),
                    sessions =
                        listOf(
                            session(
                                dia = "2026-08-17",
                                tipo = "TEMPO",
                                ritmoTipo = "RELATIVO",
                                ritmoReferencia = "10K",
                                ritmoDeltaSegundosPorKm = 10,
                            ),
                        ),
                )

            listener.on(event)

            // 2400s en 10.000m = 240 s/km; + 10 de delta = 250.
            val resolved = singleWrittenSession(projection)
            resolved.pace shouldBe ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = 10, secondsPerKm = 250)
        }

        test("dos alumnos del mismo plan con marcas distintas resuelven ritmos distintos") {
            val withMark = UUID.randomUUID()
            val withoutMark = UUID.randomUUID()
            marks.put(
                StudentId.of(withMark),
                markTenK(),
            )
            val event =
                planPublicado(
                    students = listOf(withMark, withoutMark),
                    sessions =
                        listOf(
                            session(
                                dia = "2026-08-17",
                                tipo = "TEMPO",
                                ritmoTipo = "RELATIVO",
                                ritmoReferencia = "10K",
                                ritmoDeltaSegundosPorKm = 10,
                            ),
                        ),
                )

            listener.on(event)

            val write = projection.written.single()
            write.sessionsByStudent[StudentId.of(withMark)]!!.single().pace shouldBe
                ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = 10, secondsPerKm = 250)
            write.sessionsByStudent[StudentId.of(withoutMark)]!!.single().pace shouldBe
                ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = 10, secondsPerKm = null)
        }

        test("un ritmo relativo sin delta en el evento no se resuelve aunque el alumno tenga marca") {
            val student = UUID.randomUUID()
            marks.put(
                StudentId.of(student),
                markTenK(),
            )
            val event =
                planPublicado(
                    students = listOf(student),
                    sessions =
                        listOf(
                            session(
                                dia = "2026-08-17",
                                tipo = "TEMPO",
                                ritmoTipo = "RELATIVO",
                                ritmoReferencia = "10K",
                                ritmoDeltaSegundosPorKm = null,
                            ),
                        ),
                )

            listener.on(event)

            val resolved = singleWrittenSession(projection)
            resolved.pace shouldBe
                ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = null, secondsPerKm = null)
        }

        test("una sesion de descanso sin volumen ni ritmo resuelve ambos a null") {
            val event = planPublicado(sessions = listOf(session(dia = "2026-08-17", tipo = "DESCANSO")))

            listener.on(event)

            val resolved = singleWrittenSession(projection)
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

            singleWrittenSession(projection).volume shouldBe SessionVolume.Distance(8000)
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

            singleWrittenSession(projection).volume shouldBe SessionVolume.Duration(45)
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
                .sessionsByStudent
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

/** Único alumno, única sesión: la forma más común de los tests de este fichero. */
private fun singleWrittenSession(projection: RecordingResolvedPlanProjection): ResolvedSession =
    projection.written
        .single()
        .sessionsByStudent.values
        .single()
        .single()

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

/** 2400s en 10.000m = 240 s/km. */
private fun markTenK() =
    StudentMark(RaceDistance.TEN_K, timeSeconds = 2_400, modifiedAt = Instant.parse("2026-08-01T00:00:00Z"))

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
    val sessionsByStudent: Map<StudentId, List<ResolvedSession>>,
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
        sessionsByStudent: Map<StudentId, List<ResolvedSession>>,
        eventId: UUID,
        occurredAt: Instant,
    ) {
        MDC.getCopyOfContextMap()?.let(mdcSnapshot::putAll)
        failure?.let { throw it }
        written += Write(clubId, planId, sessionsByStudent, eventId, occurredAt)
    }

    override fun lagSeconds(): Long = 0L

    override fun writePersonalizedSession(
        clubId: ClubId,
        studentId: StudentId,
        session: ResolvedSession,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean = error("No lo usa este listener — ver PersonalizationProjectionListenerTest (LAL-26)")

    override fun recalculateRelativePaces(
        clubId: ClubId,
        studentId: StudentId,
        distance: RaceDistance,
        markPaceSecondsPerKm: Int?,
    ): Int = error("No lo usa este listener — ver MarkPaceRecalculationListenerTest (LAL-32)")
}

/**
 * Doble de [ProcessedEventTracker] con la misma semántica que la clave primaria de `evento_procesado`.
 * `internal` y no `private`: lo comparten los listeners del módulo (ver `SeguimientoDeletionListenerTest`),
 * y dos declaraciones privadas con el mismo nombre en el mismo paquete colisionarían al compilar.
 */
internal class InMemoryProcessedEventTracker : ProcessedEventTracker {
    private val processed = mutableSetOf<Pair<String, UUID>>()

    override fun markIfNew(
        listener: String,
        eventId: UUID,
    ): Boolean = processed.add(listener to eventId)
}

/**
 * Doble de [StudentMarkLookup] (LAL-32), compartido por los listeners que resuelven ritmos relativos —
 * mismo criterio que [InMemoryProcessedEventTracker]. Ignora `clubId`: en estos tests unitarios cada club es
 * distinto por construcción (`UUID.randomUUID()` en cada fixture), así que no hace falta simular el filtro
 * real (eso lo prueba `StudentMarkLookupJdbc` contra Postgres).
 */
internal class InMemoryStudentMarkLookup : StudentMarkLookup {
    private val marks = mutableMapOf<StudentId, MutableMap<RaceDistance, StudentMark>>()

    fun put(
        studentId: StudentId,
        mark: StudentMark,
    ) {
        marks.getOrPut(studentId) { mutableMapOf() }[mark.distance] = mark
    }

    override fun findMark(
        clubId: ClubId,
        studentId: StudentId,
        distance: RaceDistance,
    ): StudentMark? = marks[studentId]?.get(distance)

    override fun findMarks(
        clubId: ClubId,
        students: Set<StudentId>,
    ): Map<StudentId, Map<RaceDistance, StudentMark>> =
        students.mapNotNull { student -> marks[student]?.let { student to it.toMap() } }.toMap()
}

internal object ConstantUserIdHasher : UserIdHasher {
    const val HASH = "hash-del-actor"

    override fun hash(userId: UUID): String = HASH
}
