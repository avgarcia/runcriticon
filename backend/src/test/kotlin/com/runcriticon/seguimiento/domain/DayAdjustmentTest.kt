package com.runcriticon.seguimiento.domain

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DayAdjustmentTest :
    FunSpec({
        val now = Instant.parse("2026-09-02T18:00:00Z")
        val operationId = UUID.randomUUID()
        val plannedDay = LocalDate.parse("2026-09-02")
        val targetDay = LocalDate.parse("2026-09-03")

        test("MOVIDA con destino es valido") {
            val adjustment =
                DayAdjustment
                    .create(
                        operationId = operationId,
                        action = AdjustmentAction.MOVIDA,
                        plannedDay = plannedDay,
                        targetDay = targetDay,
                        reason = AdjustmentReason.CANSANCIO,
                        message = null,
                        createdAt = now,
                    ).shouldBeRight()

            adjustment.action shouldBe AdjustmentAction.MOVIDA
            adjustment.targetDay shouldBe targetDay
            adjustment.plannedDay shouldBe plannedDay
        }

        test("MOVIDA sin destino es InvalidInput") {
            DayAdjustment
                .create(
                    operationId = operationId,
                    action = AdjustmentAction.MOVIDA,
                    plannedDay = plannedDay,
                    targetDay = null,
                    reason = AdjustmentReason.CANSANCIO,
                    message = null,
                    createdAt = now,
                ).shouldBeLeft(SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_required"))
        }

        test("MOVIDA con destino igual al dia planificado es InvalidInput") {
            DayAdjustment
                .create(
                    operationId = operationId,
                    action = AdjustmentAction.MOVIDA,
                    plannedDay = plannedDay,
                    targetDay = plannedDay,
                    reason = AdjustmentReason.CANSANCIO,
                    message = null,
                    createdAt = now,
                ).shouldBeLeft(
                    SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_same_as_origin"),
                )
        }

        test("SALTADA sin destino es valido") {
            val adjustment =
                DayAdjustment
                    .create(
                        operationId = operationId,
                        action = AdjustmentAction.SALTADA,
                        plannedDay = plannedDay,
                        targetDay = null,
                        reason = AdjustmentReason.IMPREVISTO,
                        message = null,
                        createdAt = now,
                    ).shouldBeRight()

            adjustment.targetDay shouldBe null
        }

        test("SALTADA con destino es InvalidInput") {
            DayAdjustment
                .create(
                    operationId = operationId,
                    action = AdjustmentAction.SALTADA,
                    plannedDay = plannedDay,
                    targetDay = targetDay,
                    reason = AdjustmentReason.IMPREVISTO,
                    message = null,
                    createdAt = now,
                ).shouldBeLeft(SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_not_allowed"))
        }

        test("motivo MOLESTIAS activa painFlag automaticamente") {
            val adjustment =
                DayAdjustment
                    .create(
                        operationId = operationId,
                        action = AdjustmentAction.SALTADA,
                        plannedDay = plannedDay,
                        targetDay = null,
                        reason = AdjustmentReason.MOLESTIAS,
                        message = null,
                        createdAt = now,
                    ).shouldBeRight()

            adjustment.painFlag shouldBe true
        }

        test("un motivo distinto de MOLESTIAS deja painFlag en false") {
            val adjustment =
                DayAdjustment
                    .create(
                        operationId = operationId,
                        action = AdjustmentAction.SALTADA,
                        plannedDay = plannedDay,
                        targetDay = null,
                        reason = AdjustmentReason.CANSANCIO,
                        message = null,
                        createdAt = now,
                    ).shouldBeRight()

            adjustment.painFlag shouldBe false
        }

        test("mensaje de mas de 1000 caracteres es InvalidInput") {
            val longMessage = "a".repeat(1001)

            DayAdjustment
                .create(
                    operationId = operationId,
                    action = AdjustmentAction.SALTADA,
                    plannedDay = plannedDay,
                    targetDay = null,
                    reason = AdjustmentReason.CANSANCIO,
                    message = longMessage,
                    createdAt = now,
                ).shouldBeLeft(SeguimientoError.InvalidInput(field = "mensaje", reason = "message_too_long"))
        }

        test("mensaje de exactamente 1000 caracteres es valido") {
            val message = "a".repeat(1000)

            DayAdjustment
                .create(
                    operationId = operationId,
                    action = AdjustmentAction.SALTADA,
                    plannedDay = plannedDay,
                    targetDay = null,
                    reason = AdjustmentReason.CANSANCIO,
                    message = message,
                    createdAt = now,
                ).shouldBeRight()
        }

        test("sin mensaje es valido") {
            DayAdjustment
                .create(
                    operationId = operationId,
                    action = AdjustmentAction.SALTADA,
                    plannedDay = plannedDay,
                    targetDay = null,
                    reason = AdjustmentReason.CANSANCIO,
                    message = null,
                    createdAt = now,
                ).shouldBeRight()
        }
    })
