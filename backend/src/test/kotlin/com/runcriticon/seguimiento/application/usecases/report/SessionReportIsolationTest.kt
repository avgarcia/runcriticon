package com.runcriticon.seguimiento.application.usecases.report

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.application.usecases.plan.InMemoryResolvedPlanReader
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.ReportStatus
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionType
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.testing.MutableClock
import com.runcriticon.testing.TestPrincipals
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.LocalDate

/**
 * Aislamiento entre dos alumnos del mismo club (ADR-0009 D14): `SubmitSessionReportCommand` deriva siempre
 * `StudentId.of(actor.userId)` (anti-IDOR, ver su propio KDoc) — sin `alumnoId` como parámetro no hay
 * `Forbidden` que probar. Verifica que el consentimiento, la lectura del día y la escritura del reporte llevan
 * siempre el `studentId` de quien reporta, nunca el de otro alumno.
 */
class SessionReportIsolationTest :
    FunSpec({
        val (alumnoA, alumnoB) = TestPrincipals.twoStudentsSameClub()
        val studentIdA = StudentId.of(alumnoA.userId)
        val studentIdB = StudentId.of(alumnoB.userId)
        val day = LocalDate.parse("2026-08-17")
        val planId = PlanId.of(UuidCreator.getTimeOrderedEpoch())
        val session = ResolvedSession(day = day, planId = planId, type = SessionType.TEMPO)
        val clock = MutableClock(Instant.parse("2026-08-17T18:00:00Z"))

        test("SubmitSessionReportCommand consulta y escribe siempre bajo el studentId de quien reporta") {
            val reader = InMemoryResolvedPlanReader(listOf(session))
            val repository = InMemorySessionReportRepository()
            val consentReader = InMemoryConsentReader()
            val command =
                SubmitSessionReportCommand(
                    reader,
                    repository,
                    consentReader,
                    mockk<ApplicationEventPublisher>(relaxed = true),
                    InMemorySeguimientoMetrics(),
                    clock,
                )

            command
                .execute(alumnoA, day, ReportStatus.HECHO, rating = 4, reason = null, notes = null)
                .shouldBeRight()
            command
                .execute(alumnoB, day, ReportStatus.HECHO, rating = 5, reason = null, notes = null)
                .shouldBeRight()

            consentReader.calls.map { it.second } shouldBe listOf(studentIdA, studentIdB)
            reader.dayCalls.map { it.studentId } shouldBe listOf(studentIdA, studentIdB)
            repository.calls.map { it.studentId } shouldBe listOf(studentIdA, studentIdB)
        }
    })
