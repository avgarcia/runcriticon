package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.planificacion.api.events.PersonalizacionAplicada
import com.runcriticon.planificacion.api.events.PersonalizacionRetirada
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ResolvedPace
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
import java.time.LocalDate
import java.util.UUID

/**
 * Comportamiento del listener aislado de la base de datos (LAL-26), mismo patrón que
 * `ResolvedPlanProjectionListenerTest`: qué le pide a la proyección por cada evento, y que un evento ya
 * procesado no vuelve a escribir. La guarda de orden real y el `UPDATE`-only solo los verifica un Postgres
 * real (test de flujo end-to-end, pendiente en `PersonalizationProjectionEventFlowIntegrationTest`).
 *
 * La resolución del ritmo relativo (LAL-32) en ambas ramas (`override`/`baseSession`) se prueba aquí, mismo
 * criterio que `ResolvedPlanProjectionListenerTest`.
 */
class PersonalizationProjectionListenerTest :
    FunSpec({
        lateinit var projection: RecordingPersonalizationProjection
        lateinit var marks: InMemoryStudentMarkLookup
        lateinit var processedEvents: InMemoryProcessedEventTracker
        lateinit var listener: PersonalizationProjectionListener

        beforeEach {
            projection = RecordingPersonalizationProjection()
            marks = InMemoryStudentMarkLookup()
            processedEvents = InMemoryProcessedEventTracker()
            listener =
                PersonalizationProjectionListener(
                    projection = projection,
                    marks = marks,
                    processedEvents = processedEvents,
                    mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher),
                )
        }

        afterEach { MDC.clear() }

        test("PersonalizacionAplicada escribe el override con isPersonalized true") {
            val event = personalizacionAplicada()

            listener.on(event)

            val write = projection.written.single()
            write.clubId shouldBe ClubId.of(event.clubId)
            write.studentId shouldBe StudentId.of(event.alumnoId)
            write.session.day shouldBe event.dia
            write.session.planId shouldBe PlanId.of(event.aggregateId)
            write.session.messageToStudent shouldBe event.mensajeAlAlumno
            write.session.isPersonalized shouldBe true
            write.eventId shouldBe event.eventId
        }

        test("PersonalizacionRetirada escribe la sesion base con isPersonalized false y sin mensaje") {
            val event = personalizacionRetirada()

            listener.on(event)

            val write = projection.written.single()
            write.session.day shouldBe event.dia
            write.session.messageToStudent.shouldBeNull()
            write.session.isPersonalized shouldBe false
        }

        test("un PersonalizacionAplicada ya procesado no vuelve a escribir") {
            val event = personalizacionAplicada()
            listener.on(event)

            listener.on(event)

            projection.written shouldHaveSize 1
        }

        test("un PersonalizacionRetirada ya procesado no vuelve a escribir") {
            val event = personalizacionRetirada()
            listener.on(event)

            listener.on(event)

            projection.written shouldHaveSize 1
        }

        test("PersonalizacionAplicada con ritmo relativo y el alumno con marca resuelve marca + delta (LAL-32)") {
            val alumnoId = UUID.randomUUID()
            marks.put(
                StudentId.of(alumnoId),
                mark(RaceDistance.TEN_K, timeSeconds = 2_400),
            )
            val event =
                personalizacionAplicada(
                    alumnoId = alumnoId,
                    override =
                        personalizedSession(
                            tipo = "TEMPO",
                            ritmoTipo = "RELATIVO",
                            ritmoReferencia = "10K",
                            ritmoDeltaSegundosPorKm = 10,
                        ),
                )

            listener.on(event)

            projection.written
                .single()
                .session.pace shouldBe
                ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = 10, secondsPerKm = 250)
        }

        test("PersonalizacionAplicada con ritmo relativo y el alumno sin marca resuelve falta de marca") {
            val event =
                personalizacionAplicada(
                    override =
                        personalizedSession(
                            tipo = "TEMPO",
                            ritmoTipo = "RELATIVO",
                            ritmoReferencia = "21K",
                            ritmoDeltaSegundosPorKm = -5,
                        ),
                )

            listener.on(event)

            projection.written
                .single()
                .session.pace shouldBe
                ResolvedPace.Relative(RaceDistance.HALF_MARATHON, deltaSecondsPerKm = -5, secondsPerKm = null)
        }

        test("PersonalizacionRetirada con ritmo relativo en la sesion base resuelve contra la marca del alumno") {
            val alumnoId = UUID.randomUUID()
            marks.put(
                StudentId.of(alumnoId),
                mark(RaceDistance.FIVE_K, timeSeconds = 1_200),
            )
            val event =
                personalizacionRetirada(
                    alumnoId = alumnoId,
                    baseSession =
                        personalizedSession(
                            tipo = "RODAJE",
                            ritmoTipo = "RELATIVO",
                            ritmoReferencia = "5K",
                            ritmoDeltaSegundosPorKm = 20,
                        ),
                )

            listener.on(event)

            // 1200s en 5.000m = 240 s/km; + 20 de delta = 260.
            projection.written
                .single()
                .session.pace shouldBe
                ResolvedPace.Relative(RaceDistance.FIVE_K, deltaSecondsPerKm = 20, secondsPerKm = 260)
        }
    })

private fun mark(
    distance: RaceDistance,
    timeSeconds: Int,
) = StudentMark(distance, timeSeconds, modifiedAt = Instant.parse("2026-08-01T00:00:00Z"))

private fun personalizedSession(
    tipo: String = "DESCANSO",
    ritmoTipo: String? = null,
    ritmoSegundosPorKm: Int? = null,
    ritmoReferencia: String? = null,
    ritmoDeltaSegundosPorKm: Int? = null,
) = PersonalizedSession(
    tipo = tipo,
    volumenTipo = null,
    volumenMetros = null,
    volumenMinutos = null,
    ritmoTipo = ritmoTipo,
    ritmoSegundosPorKm = ritmoSegundosPorKm,
    ritmoReferencia = ritmoReferencia,
    ritmoDeltaSegundosPorKm = ritmoDeltaSegundosPorKm,
    notas = null,
)

private fun personalizacionAplicada(
    planId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
    alumnoId: UUID = UUID.randomUUID(),
    override: PersonalizedSession = personalizedSession(),
) = PersonalizacionAplicada(
    eventId = UUID.randomUUID(),
    aggregateId = planId,
    occurredAt = Instant.parse("2026-08-20T07:30:00Z"),
    clubId = clubId,
    actorId = null,
    traceparent = null,
    grupoId = UUID.randomUUID(),
    sesionId = UUID.randomUUID(),
    dia = LocalDate.of(2026, 8, 20),
    alumnoId = alumnoId,
    override = override,
    mensajeAlAlumno = "Descansa hoy",
)

private fun personalizacionRetirada(
    planId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
    alumnoId: UUID = UUID.randomUUID(),
    baseSession: PersonalizedSession = personalizedSession(tipo = "RODAJE"),
) = PersonalizacionRetirada(
    eventId = UUID.randomUUID(),
    aggregateId = planId,
    occurredAt = Instant.parse("2026-08-21T07:30:00Z"),
    clubId = clubId,
    actorId = null,
    traceparent = null,
    grupoId = UUID.randomUUID(),
    sesionId = UUID.randomUUID(),
    dia = LocalDate.of(2026, 8, 21),
    alumnoId = alumnoId,
    baseSession = baseSession,
)

/** Escritura registrada en la proyección. */
private data class PersonalizationWrite(
    val clubId: ClubId,
    val studentId: StudentId,
    val session: ResolvedSession,
    val eventId: UUID,
    val occurredAt: Instant,
)

/** Doble de [ResolvedPlanProjection] que apunta lo que se le pide escribir vía `writePersonalizedSession`. */
private class RecordingPersonalizationProjection : ResolvedPlanProjection {
    val written = mutableListOf<PersonalizationWrite>()

    override fun replacePlan(
        clubId: ClubId,
        planId: PlanId,
        groupId: GroupId,
        sessionsByStudent: Map<StudentId, List<ResolvedSession>>,
        eventId: UUID,
        occurredAt: Instant,
    ) = error("no usado en PersonalizationProjectionListenerTest")

    override fun lagSeconds(): Long = error("no usado en PersonalizationProjectionListenerTest")

    override fun writePersonalizedSession(
        clubId: ClubId,
        studentId: StudentId,
        session: ResolvedSession,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean {
        written += PersonalizationWrite(clubId, studentId, session, eventId, occurredAt)
        return true
    }

    override fun recalculateRelativePaces(
        clubId: ClubId,
        studentId: StudentId,
        distance: RaceDistance,
        markPaceSecondsPerKm: Int?,
    ): Int = error("no usado en PersonalizationProjectionListenerTest")
}
