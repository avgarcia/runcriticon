package com.runcriticon.seguimiento.application.usecases.adjustment

import com.runcriticon.seguimiento.application.RecordingAccessAuditor
import com.runcriticon.seguimiento.application.usecases.plan.InMemoryResolvedPlanReader
import com.runcriticon.seguimiento.application.usecases.report.InMemoryConsentReader
import com.runcriticon.seguimiento.application.usecases.report.InMemorySeguimientoMetrics
import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.AdjustmentReason
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.testing.MutableClock
import com.runcriticon.testing.PrincipalBuilder
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.LocalDate

/** Solo el ALUMNO reajusta sus sesiones; el rechazo no toca ni el lector ni el repositorio. */
class RescheduleDayAuthorizationTest :
    FunSpec({
        val clock = MutableClock(Instant.parse("2026-09-02T18:00:00Z"))

        fun principal(role: Role) = PrincipalBuilder().role(role).build()

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role no puede reajustar una sesion, y no se toca ni el lector ni el repositorio") {
                val reader = InMemoryResolvedPlanReader()
                val repository = InMemoryDayAdjustmentRepository()
                val consentReader = InMemoryConsentReader()
                val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
                val metrics = InMemorySeguimientoMetrics()
                val audit = RecordingAccessAuditor()
                val command =
                    RescheduleDayCommand(
                        reader,
                        repository,
                        consentReader,
                        eventPublisher,
                        metrics,
                        clock,
                        audit.auditor,
                    )
                val actor = principal(role)

                command
                    .execute(
                        actor,
                        LocalDate.parse("2026-09-02"),
                        AdjustmentAction.SALTADA,
                        targetDay = null,
                        reason = AdjustmentReason.CANSANCIO,
                        message = null,
                        conflictResolution = null,
                    ).shouldBeLeft(SeguimientoError.Forbidden)

                reader.dayCalls.size shouldBe 0
                repository.calls.size shouldBe 0
                consentReader.calls.size shouldBe 0
                metrics.reschedules.size shouldBe 0
                audit.shouldHaveDeniedRbac(actor, "DAY_ADJUSTMENT:RESCHEDULE")
            }

            test("$role no puede deshacer un reajuste, y no se toca ni el lector ni el repositorio") {
                val reader = InMemoryResolvedPlanReader()
                val repository = InMemoryDayAdjustmentRepository()
                val audit = RecordingAccessAuditor()
                val command = WithdrawDayAdjustmentCommand(reader, repository, audit.auditor)
                val actor = principal(role)

                command
                    .execute(actor, LocalDate.parse("2026-09-02"))
                    .shouldBeLeft(SeguimientoError.Forbidden)

                reader.dayCalls.size shouldBe 0
                repository.deletedOperations.size shouldBe 0
                audit.shouldHaveDeniedRbac(actor, "DAY_ADJUSTMENT:WITHDRAW")
            }
        }
    })
