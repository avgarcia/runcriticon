package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.planificacion.api.events.PersonalizacionAplicada
import com.runcriticon.planificacion.api.events.PersonalizacionRetirada
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.StudentId
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
 */
class PersonalizationProjectionListenerTest :
    FunSpec({
        lateinit var projection: RecordingPersonalizationProjection
        lateinit var processedEvents: InMemoryProcessedEventTracker
        lateinit var listener: PersonalizationProjectionListener

        beforeEach {
            projection = RecordingPersonalizationProjection()
            processedEvents = InMemoryProcessedEventTracker()
            listener =
                PersonalizationProjectionListener(
                    projection = projection,
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
    })

private fun personalizedSession(tipo: String = "DESCANSO") =
    PersonalizedSession(
        tipo = tipo,
        volumenTipo = null,
        volumenMetros = null,
        volumenMinutos = null,
        ritmoTipo = null,
        ritmoSegundosPorKm = null,
        ritmoReferencia = null,
        ritmoDeltaSegundosPorKm = null,
        notas = null,
    )

private fun personalizacionAplicada(
    planId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
    alumnoId: UUID = UUID.randomUUID(),
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
    override = personalizedSession(),
    mensajeAlAlumno = "Descansa hoy",
)

private fun personalizacionRetirada(
    planId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
    alumnoId: UUID = UUID.randomUUID(),
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
    baseSession = personalizedSession(tipo = "RODAJE"),
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
        students: Set<StudentId>,
        sessions: List<ResolvedSession>,
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
}
