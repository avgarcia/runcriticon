package com.runcriticon.seguimiento.application.usecases.adjustment

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.application.usecases.plan.InMemoryResolvedPlanReader
import com.runcriticon.seguimiento.application.usecases.report.InMemoryConsentReader
import com.runcriticon.seguimiento.application.usecases.report.InMemorySeguimientoMetrics
import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.AdjustmentReason
import com.runcriticon.seguimiento.domain.DayAdjustment
import com.runcriticon.seguimiento.domain.PlanId
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
import java.util.UUID

/**
 * Aislamiento entre dos alumnos del mismo club (ADR-0009 D14): `RescheduleDayCommand` y
 * `WithdrawDayAdjustmentCommand` derivan siempre `StudentId.of(actor.userId)` (anti-IDOR, ver KDoc de
 * `RescheduleDayCommand`) — sin `alumnoId` como parámetro no hay `Forbidden` que probar. Lo que hay que
 * verificar es que cada llamada al lector y al repositorio lleva el `studentId` de quien la hizo, nunca el de
 * otro alumno (el filtrado real `WHERE alumno_id = ?` lo cubre el test de integración contra Postgres real,
 * no este doble en memoria).
 */
class DayAdjustmentIsolationTest :
    FunSpec({
        val (alumnoA, alumnoB) = TestPrincipals.twoStudentsSameClub()
        val studentIdA = StudentId.of(alumnoA.userId)
        val studentIdB = StudentId.of(alumnoB.userId)
        val today = LocalDate.parse("2026-09-02")
        val planId = PlanId.of(UuidCreator.getTimeOrderedEpoch())
        val clock = MutableClock(Instant.parse("2026-09-02T18:00:00Z"))

        test("RescheduleDayCommand lee y escribe siempre bajo el studentId de quien reajusta") {
            val session = ResolvedSession(day = today, planId = planId, type = SessionType.TEMPO)
            val reader = InMemoryResolvedPlanReader(listOf(session))
            val repository = InMemoryDayAdjustmentRepository()
            val command =
                RescheduleDayCommand(
                    reader,
                    repository,
                    InMemoryConsentReader(),
                    mockk<ApplicationEventPublisher>(relaxed = true),
                    InMemorySeguimientoMetrics(),
                    clock,
                )

            command
                .execute(alumnoA, today, AdjustmentAction.SALTADA, null, AdjustmentReason.CANSANCIO, null, null)
                .shouldBeRight()
            command
                .execute(alumnoB, today, AdjustmentAction.SALTADA, null, AdjustmentReason.CANSANCIO, null, null)
                .shouldBeRight()

            reader.dayCalls.map { it.studentId } shouldBe listOf(studentIdA, studentIdB)
            repository.calls.map { it.studentId } shouldBe listOf(studentIdA, studentIdB)
        }

        test("WithdrawDayAdjustmentCommand deshace siempre bajo el studentId de quien deshace") {
            val operationId = UUID.randomUUID()
            val adjustment =
                DayAdjustment(
                    operationId = operationId,
                    action = AdjustmentAction.SALTADA,
                    plannedDay = today,
                    reason = AdjustmentReason.CANSANCIO,
                    createdAt = Instant.parse("2026-09-02T18:00:00Z"),
                )
            val session =
                ResolvedSession(day = today, planId = planId, type = SessionType.TEMPO, adjustment = adjustment)
            val reader = InMemoryResolvedPlanReader(listOf(session))
            val repository = InMemoryDayAdjustmentRepository()
            val command = WithdrawDayAdjustmentCommand(reader, repository)

            command.execute(alumnoA, today).shouldBeRight()
            command.execute(alumnoB, today).shouldBeRight()

            reader.dayCalls.map { it.studentId } shouldBe listOf(studentIdA, studentIdB)
            repository.deletedBy shouldBe listOf(studentIdA, studentIdB)
        }
    })
