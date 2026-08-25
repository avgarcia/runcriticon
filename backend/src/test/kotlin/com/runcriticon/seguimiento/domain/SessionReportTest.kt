package com.runcriticon.seguimiento.domain

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class SessionReportTest :
    FunSpec({
        val now = Instant.parse("2026-08-17T18:00:00Z")

        test("HECHO con valoracion es valido") {
            val report =
                SessionReport
                    .create(status = ReportStatus.HECHO, rating = 4, reason = null, notes = null, reportedAt = now)
                    .shouldBeRight()

            report.status shouldBe ReportStatus.HECHO
            report.rating shouldBe 4
        }

        test("PARCIAL con valoracion es valido") {
            SessionReport
                .create(status = ReportStatus.PARCIAL, rating = 2, reason = null, notes = null, reportedAt = now)
                .shouldBeRight()
        }

        test("HECHO sin valoracion es InvalidInput") {
            SessionReport
                .create(status = ReportStatus.HECHO, rating = null, reason = null, notes = null, reportedAt = now)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "valoracion", reason = "rating_required"))
        }

        test("PARCIAL sin valoracion es InvalidInput") {
            SessionReport
                .create(status = ReportStatus.PARCIAL, rating = null, reason = null, notes = null, reportedAt = now)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "valoracion", reason = "rating_required"))
        }

        test("valoracion fuera de 1..5 es InvalidInput") {
            SessionReport
                .create(status = ReportStatus.HECHO, rating = 6, reason = null, notes = null, reportedAt = now)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "valoracion", reason = "rating_out_of_range"))

            SessionReport
                .create(status = ReportStatus.HECHO, rating = 0, reason = null, notes = null, reportedAt = now)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "valoracion", reason = "rating_out_of_range"))
        }

        test("HECHO con motivo es InvalidInput: el motivo no aplica si se hizo") {
            SessionReport
                .create(
                    status = ReportStatus.HECHO,
                    rating = 4,
                    reason = NotDoneReason.CANSANCIO,
                    notes = null,
                    reportedAt = now,
                ).shouldBeLeft(SeguimientoError.InvalidInput(field = "motivo", reason = "reason_not_allowed"))
        }

        test("NO_HECHO con motivo y sin valoracion es valido") {
            val report =
                SessionReport
                    .create(
                        status = ReportStatus.NO_HECHO,
                        rating = null,
                        reason = NotDoneReason.TRABAJO,
                        notes = null,
                        reportedAt = now,
                    ).shouldBeRight()

            report.rating shouldBe null
            report.reason shouldBe NotDoneReason.TRABAJO
        }

        test("NO_HECHO sin motivo es InvalidInput") {
            SessionReport
                .create(status = ReportStatus.NO_HECHO, rating = null, reason = null, notes = null, reportedAt = now)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "motivo", reason = "reason_required"))
        }

        test("NO_HECHO con valoracion es InvalidInput: no procede valorar lo que no se hizo") {
            SessionReport
                .create(
                    status = ReportStatus.NO_HECHO,
                    rating = 3,
                    reason = NotDoneReason.TRABAJO,
                    notes = null,
                    reportedAt = now,
                ).shouldBeLeft(SeguimientoError.InvalidInput(field = "valoracion", reason = "rating_not_allowed"))
        }

        test("motivo MOLESTIAS activa painFlag automaticamente") {
            val report =
                SessionReport
                    .create(
                        status = ReportStatus.NO_HECHO,
                        rating = null,
                        reason = NotDoneReason.MOLESTIAS,
                        notes = null,
                        reportedAt = now,
                    ).shouldBeRight()

            report.painFlag shouldBe true
        }

        test("un motivo distinto de MOLESTIAS deja painFlag en false") {
            val report =
                SessionReport
                    .create(
                        status = ReportStatus.NO_HECHO,
                        rating = null,
                        reason = NotDoneReason.VIAJE,
                        notes = null,
                        reportedAt = now,
                    ).shouldBeRight()

            report.painFlag shouldBe false
        }

        test("notas de mas de 1000 caracteres es InvalidInput") {
            val longNotes = "a".repeat(1001)

            SessionReport
                .create(status = ReportStatus.HECHO, rating = 4, reason = null, notes = longNotes, reportedAt = now)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "notas", reason = "notes_too_long"))
        }

        test("notas de exactamente 1000 caracteres es valido") {
            val notes = "a".repeat(1000)

            SessionReport
                .create(status = ReportStatus.HECHO, rating = 4, reason = null, notes = notes, reportedAt = now)
                .shouldBeRight()
        }

        test("sin notas es valido") {
            SessionReport
                .create(status = ReportStatus.HECHO, rating = 4, reason = null, notes = null, reportedAt = now)
                .shouldBeRight()
        }
    })
