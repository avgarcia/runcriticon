package com.runcriticon.seguimiento.application.usecases.adjustment

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.application.usecases.plan.InMemoryResolvedPlanReader
import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.AdjustmentReason
import com.runcriticon.seguimiento.domain.DayAdjustment
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionType
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WithdrawDayAdjustmentCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val alumno = Principal(userId = UuidCreator.getTimeOrderedEpoch(), clubId = club.value, role = Role.ALUMNO)
        val day = LocalDate.parse("2026-09-02")
        val planId = PlanId.of(UuidCreator.getTimeOrderedEpoch())

        test("deshacer un reajuste existente borra por operationId") {
            val operationId = UUID.randomUUID()
            val adjustment =
                DayAdjustment(
                    operationId = operationId,
                    action = AdjustmentAction.SALTADA,
                    plannedDay = day,
                    reason = AdjustmentReason.CANSANCIO,
                    createdAt = Instant.parse("2026-09-02T18:00:00Z"),
                )
            val session = ResolvedSession(day = day, planId = planId, type = SessionType.TEMPO, adjustment = adjustment)
            val reader = InMemoryResolvedPlanReader(listOf(session))
            val repository = InMemoryDayAdjustmentRepository()
            val command = WithdrawDayAdjustmentCommand(reader, repository)

            command.execute(alumno, day).shouldBeRight()

            repository.deletedOperations shouldBe listOf(operationId)
        }

        test("deshacer un dia sin reajuste es idempotente y no toca el repositorio") {
            val session = ResolvedSession(day = day, planId = planId, type = SessionType.TEMPO)
            val reader = InMemoryResolvedPlanReader(listOf(session))
            val repository = InMemoryDayAdjustmentRepository()
            val command = WithdrawDayAdjustmentCommand(reader, repository)

            command.execute(alumno, day).shouldBeRight()

            repository.deletedOperations.size shouldBe 0
        }

        test("deshacer un dia sin sesion es idempotente y no toca el repositorio") {
            val reader = InMemoryResolvedPlanReader(emptyList())
            val repository = InMemoryDayAdjustmentRepository()
            val command = WithdrawDayAdjustmentCommand(reader, repository)

            command.execute(alumno, day).shouldBeRight()

            repository.deletedOperations.size shouldBe 0
        }
    })
