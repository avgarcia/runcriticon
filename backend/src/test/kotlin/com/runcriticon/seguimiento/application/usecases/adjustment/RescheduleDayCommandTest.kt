package com.runcriticon.seguimiento.application.usecases.adjustment

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.api.events.DiaReajustado
import com.runcriticon.seguimiento.application.usecases.plan.InMemoryResolvedPlanReader
import com.runcriticon.seguimiento.application.usecases.report.InMemoryConsentReader
import com.runcriticon.seguimiento.application.usecases.report.InMemorySeguimientoMetrics
import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.AdjustmentReason
import com.runcriticon.seguimiento.domain.ConflictResolution
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.seguimiento.domain.SessionType
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.MutableClock
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RescheduleDayCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val alumno = Principal(userId = UuidCreator.getTimeOrderedEpoch(), clubId = club.value, role = Role.ALUMNO)
        val today = LocalDate.parse("2026-09-02")
        val planId = PlanId.of(UuidCreator.getTimeOrderedEpoch())
        val originSession = ResolvedSession(day = today, planId = planId, type = SessionType.TEMPO)
        val clock = MutableClock(Instant.parse("2026-09-02T18:00:00Z"))

        fun newCommand(
            reader: InMemoryResolvedPlanReader = InMemoryResolvedPlanReader(listOf(originSession)),
            repository: InMemoryDayAdjustmentRepository = InMemoryDayAdjustmentRepository(),
            consentReader: InMemoryConsentReader = InMemoryConsentReader(),
            eventPublisher: ApplicationEventPublisher = mockk(relaxed = true),
            metrics: InMemorySeguimientoMetrics = InMemorySeguimientoMetrics(),
        ) = RescheduleDayCommand(reader, repository, consentReader, eventPublisher, metrics, clock)

        test("MOVER a un dia libre se guarda y publica DiaReajustado") {
            val targetDay = today.plusDays(3)
            val repository = InMemoryDayAdjustmentRepository()
            val metrics = InMemorySeguimientoMetrics()
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<DiaReajustado>()
            val command = newCommand(repository = repository, metrics = metrics, eventPublisher = eventPublisher)

            val result =
                command
                    .execute(
                        alumno,
                        today,
                        AdjustmentAction.MOVIDA,
                        targetDay,
                        AdjustmentReason.CANSANCIO,
                        message = null,
                        conflictResolution = null,
                    ).shouldBeRight()

            result.action shouldBe AdjustmentAction.MOVIDA
            result.targetDay shouldBe targetDay
            repository.calls shouldHaveSize 1
            repository.calls.single().planId shouldBe planId
            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.accion shouldBe "MOVIDA"
            slot.captured.diaDestino shouldBe targetDay
            slot.captured.planId shouldBe planId.value
            metrics.reschedules shouldBe listOf(AdjustmentAction.MOVIDA)
        }

        test("SALTAR se guarda sin destino") {
            val repository = InMemoryDayAdjustmentRepository()
            val command = newCommand(repository = repository)

            command
                .execute(
                    alumno,
                    today,
                    AdjustmentAction.SALTADA,
                    targetDay = null,
                    reason = AdjustmentReason.IMPREVISTO,
                    message = null,
                    conflictResolution = null,
                ).shouldBeRight()

            repository.calls
                .single()
                .adjustment.action shouldBe AdjustmentAction.SALTADA
            repository.calls
                .single()
                .adjustment.targetDay shouldBe null
        }

        test("motivo MOLESTIAS activa marcaDolor en el evento y en el resultado") {
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<DiaReajustado>()
            val command = newCommand(eventPublisher = eventPublisher)

            val result =
                command
                    .execute(
                        alumno,
                        today,
                        AdjustmentAction.SALTADA,
                        targetDay = null,
                        reason = AdjustmentReason.MOLESTIAS,
                        message = null,
                        conflictResolution = null,
                    ).shouldBeRight()

            result.painFlag shouldBe true
            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.marcaDolor shouldBe true
        }

        test("sin consentimiento vigente es ConsentNotGranted y no toca el lector ni el repositorio") {
            val reader = InMemoryResolvedPlanReader(listOf(originSession))
            val repository = InMemoryDayAdjustmentRepository()
            val consentReader = InMemoryConsentReader(granted = false)
            val command = newCommand(reader = reader, repository = repository, consentReader = consentReader)

            command
                .execute(
                    alumno,
                    today,
                    AdjustmentAction.SALTADA,
                    targetDay = null,
                    reason = AdjustmentReason.CANSANCIO,
                    message = null,
                    conflictResolution = null,
                ).shouldBeLeft(SeguimientoError.ConsentNotGranted)

            reader.dayCalls.size shouldBe 0
            repository.calls.size shouldBe 0
        }

        test("reajustar un dia sin sesion es SessionNotFound") {
            val reader = InMemoryResolvedPlanReader(emptyList())
            val repository = InMemoryDayAdjustmentRepository()
            val command = newCommand(reader = reader, repository = repository)

            command
                .execute(
                    alumno,
                    today,
                    AdjustmentAction.SALTADA,
                    targetDay = null,
                    reason = AdjustmentReason.CANSANCIO,
                    message = null,
                    conflictResolution = null,
                ).shouldBeLeft(SeguimientoError.SessionNotFound)

            repository.calls.size shouldBe 0
        }

        test("un dia pasado es InvalidInput past_day y no persiste nada") {
            val pastDay = today.minusDays(1)
            val pastSession = ResolvedSession(day = pastDay, planId = planId, type = SessionType.RODAJE)
            val reader = InMemoryResolvedPlanReader(listOf(pastSession))
            val repository = InMemoryDayAdjustmentRepository()
            val command = newCommand(reader = reader, repository = repository)

            command
                .execute(
                    alumno,
                    pastDay,
                    AdjustmentAction.MOVIDA,
                    today.plusDays(1),
                    AdjustmentReason.CANSANCIO,
                    message = null,
                    conflictResolution = null,
                ).shouldBeLeft(SeguimientoError.InvalidInput(field = "dia", reason = "past_day"))

            repository.calls.size shouldBe 0
        }

        test("un destino en el pasado es InvalidInput target_day_out_of_range") {
            val repository = InMemoryDayAdjustmentRepository()
            val command = newCommand(repository = repository)

            command
                .execute(
                    alumno,
                    today,
                    AdjustmentAction.MOVIDA,
                    today.minusDays(1),
                    AdjustmentReason.CANSANCIO,
                    message = null,
                    conflictResolution = null,
                ).shouldBeLeft(
                    SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_out_of_range"),
                )

            repository.calls.size shouldBe 0
        }

        test("un destino a mas de 7 dias es InvalidInput target_day_out_of_range") {
            val repository = InMemoryDayAdjustmentRepository()
            val command = newCommand(repository = repository)

            command
                .execute(
                    alumno,
                    today,
                    AdjustmentAction.MOVIDA,
                    today.plusDays(8),
                    AdjustmentReason.CANSANCIO,
                    message = null,
                    conflictResolution = null,
                ).shouldBeLeft(
                    SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_out_of_range"),
                )

            repository.calls.size shouldBe 0
        }

        test("MOVER sin destino es InvalidInput y no persiste nada") {
            val repository = InMemoryDayAdjustmentRepository()
            val metrics = InMemorySeguimientoMetrics()
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = newCommand(repository = repository, metrics = metrics, eventPublisher = eventPublisher)

            command
                .execute(
                    alumno,
                    today,
                    AdjustmentAction.MOVIDA,
                    targetDay = null,
                    reason = AdjustmentReason.CANSANCIO,
                    message = null,
                    conflictResolution = null,
                ).shouldBeLeft(SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_required"))

            repository.calls.size shouldBe 0
            metrics.reschedules.size shouldBe 0
            verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
        }

        context("destino ocupado") {
            val occupantDay = today.plusDays(2)
            val occupantPlanId = PlanId.of(UuidCreator.getTimeOrderedEpoch())
            val occupantSession = ResolvedSession(day = occupantDay, planId = occupantPlanId, type = SessionType.SERIES)

            test("sin resolucionConflicto es TargetDayOccupied y no persiste nada") {
                val reader = InMemoryResolvedPlanReader(listOf(originSession, occupantSession))
                val repository = InMemoryDayAdjustmentRepository()
                val command = newCommand(reader = reader, repository = repository)

                command
                    .execute(
                        alumno,
                        today,
                        AdjustmentAction.MOVIDA,
                        occupantDay,
                        AdjustmentReason.CANSANCIO,
                        message = null,
                        conflictResolution = null,
                    ).shouldBeLeft(SeguimientoError.TargetDayOccupied)

                repository.calls.size shouldBe 0
            }

            test("REEMPLAZAR escribe la sesion movida y la ocupante saltada, con el mismo operationId") {
                val reader = InMemoryResolvedPlanReader(listOf(originSession, occupantSession))
                val repository = InMemoryDayAdjustmentRepository()
                val metrics = InMemorySeguimientoMetrics()
                val command = newCommand(reader = reader, repository = repository, metrics = metrics)

                val result =
                    command
                        .execute(
                            alumno,
                            today,
                            AdjustmentAction.MOVIDA,
                            occupantDay,
                            AdjustmentReason.CANSANCIO,
                            message = null,
                            conflictResolution = ConflictResolution.REEMPLAZAR,
                        ).shouldBeRight()

                repository.calls shouldHaveSize 2
                val originCall = repository.calls.first { it.planId == planId }
                val occupantCall = repository.calls.first { it.planId == occupantPlanId }

                originCall.adjustment.action shouldBe AdjustmentAction.MOVIDA
                originCall.adjustment.targetDay shouldBe occupantDay
                occupantCall.adjustment.action shouldBe AdjustmentAction.SALTADA
                occupantCall.adjustment.targetDay shouldBe null
                originCall.adjustment.operationId shouldBe occupantCall.adjustment.operationId
                result.operationId shouldBe originCall.adjustment.operationId
                metrics.reschedules shouldBe listOf(AdjustmentAction.MOVIDA, AdjustmentAction.SALTADA)
            }

            test("INTERCAMBIAR mueve las dos sesiones, cada una al dia de la otra") {
                val reader = InMemoryResolvedPlanReader(listOf(originSession, occupantSession))
                val repository = InMemoryDayAdjustmentRepository()
                val command = newCommand(reader = reader, repository = repository)

                command
                    .execute(
                        alumno,
                        today,
                        AdjustmentAction.MOVIDA,
                        occupantDay,
                        AdjustmentReason.CANSANCIO,
                        message = null,
                        conflictResolution = ConflictResolution.INTERCAMBIAR,
                    ).shouldBeRight()

                repository.calls shouldHaveSize 2
                val originCall = repository.calls.first { it.planId == planId }
                val occupantCall = repository.calls.first { it.planId == occupantPlanId }

                originCall.adjustment.action shouldBe AdjustmentAction.MOVIDA
                originCall.adjustment.targetDay shouldBe occupantDay
                occupantCall.adjustment.action shouldBe AdjustmentAction.MOVIDA
                occupantCall.adjustment.targetDay shouldBe today
                originCall.adjustment.operationId shouldBe occupantCall.adjustment.operationId
            }
        }
    })
