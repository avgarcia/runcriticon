package com.runcriticon.seguimiento.application.usecases.report

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.api.events.ReporteRegistrado
import com.runcriticon.seguimiento.application.usecases.plan.InMemoryResolvedPlanReader
import com.runcriticon.seguimiento.domain.NotDoneReason
import com.runcriticon.seguimiento.domain.PlanId
import com.runcriticon.seguimiento.domain.ReportStatus
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
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class SubmitSessionReportCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val alumno = Principal(userId = UuidCreator.getTimeOrderedEpoch(), clubId = club.value, role = Role.ALUMNO)
        val day = LocalDate.parse("2026-08-17")
        val planId = PlanId.of(UuidCreator.getTimeOrderedEpoch())
        val session = ResolvedSession(day = day, planId = planId, type = SessionType.TEMPO)
        val now = MutableClock(Instant.parse("2026-08-17T18:00:00Z"))

        fun newCommand(
            reader: InMemoryResolvedPlanReader = InMemoryResolvedPlanReader(listOf(session)),
            repository: InMemorySessionReportRepository = InMemorySessionReportRepository(),
            consentReader: InMemoryConsentReader = InMemoryConsentReader(),
            eventPublisher: ApplicationEventPublisher = mockk(relaxed = true),
            metrics: InMemorySeguimientoMetrics = InMemorySeguimientoMetrics(),
        ) = SubmitSessionReportCommand(reader, repository, consentReader, eventPublisher, metrics, now)

        test("HECHO con valoracion se guarda y devuelve la sesion con el reporte aplicado") {
            val repository = InMemorySessionReportRepository()
            val metrics = InMemorySeguimientoMetrics()
            val command = newCommand(repository = repository, metrics = metrics)

            val result =
                command
                    .execute(alumno, day, ReportStatus.HECHO, rating = 4, reason = null, notes = "bien")
                    .shouldBeRight()

            result.report?.status shouldBe ReportStatus.HECHO
            result.report?.rating shouldBe 4
            val call = repository.calls.single()
            call.clubId shouldBe club
            call.planId shouldBe planId
            call.day shouldBe day
            call.report.status shouldBe ReportStatus.HECHO
            metrics.calls shouldBe listOf(ReportStatus.HECHO)
        }

        test("NO_HECHO con motivo MOLESTIAS activa la marca de dolor") {
            val repository = InMemorySessionReportRepository()
            val command = newCommand(repository = repository)

            val result =
                command
                    .execute(
                        alumno,
                        day,
                        ReportStatus.NO_HECHO,
                        rating = null,
                        reason = NotDoneReason.MOLESTIAS,
                        notes = null,
                    ).shouldBeRight()

            result.report?.painFlag shouldBe true
        }

        test("sin consentimiento vigente es ConsentNotGranted, no toca el lector ni el repositorio") {
            val reader = InMemoryResolvedPlanReader(listOf(session))
            val repository = InMemorySessionReportRepository()
            val consentReader = InMemoryConsentReader(granted = false)
            val metrics = InMemorySeguimientoMetrics()
            val command =
                newCommand(reader = reader, repository = repository, consentReader = consentReader, metrics = metrics)

            command
                .execute(alumno, day, ReportStatus.HECHO, rating = 4, reason = null, notes = null)
                .shouldBeLeft(SeguimientoError.ConsentNotGranted)

            reader.calls.size shouldBe 0
            reader.dayCalls.size shouldBe 0
            repository.calls.size shouldBe 0
            metrics.rejections shouldBe listOf("consentimiento")
        }

        test("reportar un dia sin sesion publicada es SessionNotFound y no persiste nada") {
            val reader = InMemoryResolvedPlanReader(emptyList())
            val repository = InMemorySessionReportRepository()
            val command = newCommand(reader = reader, repository = repository)

            command
                .execute(alumno, day, ReportStatus.HECHO, rating = 4, reason = null, notes = null)
                .shouldBeLeft(SeguimientoError.SessionNotFound)

            repository.calls.size shouldBe 0
        }

        test("un dia futuro es InvalidInput y no persiste nada, aunque haya sesion ese dia") {
            val futureDay = day.plusDays(1)
            val reader =
                InMemoryResolvedPlanReader(
                    listOf(ResolvedSession(day = futureDay, planId = planId, type = SessionType.RODAJE)),
                )
            val repository = InMemorySessionReportRepository()
            val command = newCommand(reader = reader, repository = repository)

            command
                .execute(alumno, futureDay, ReportStatus.HECHO, rating = 4, reason = null, notes = null)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "dia", reason = "future_day"))

            repository.calls.size shouldBe 0
        }

        test("hoy mismo (no futuro) es un dia valido") {
            val command = newCommand()

            command
                .execute(alumno, day, ReportStatus.HECHO, rating = 4, reason = null, notes = null)
                .shouldBeRight()
        }

        test("un invariante de dominio invalido (HECHO sin valoracion) es InvalidInput y no persiste nada") {
            val repository = InMemorySessionReportRepository()
            val command = newCommand(repository = repository)

            command
                .execute(alumno, day, ReportStatus.HECHO, rating = null, reason = null, notes = null)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "valoracion", reason = "rating_required"))

            repository.calls.size shouldBe 0
        }

        test("emite ReporteRegistrado con los campos del reporte, sin notas ni descripcion del dolor") {
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<ReporteRegistrado>()
            val command = newCommand(eventPublisher = eventPublisher)

            command
                .execute(alumno, day, ReportStatus.PARCIAL, rating = 3, reason = null, notes = "una nota cualquiera")
                .shouldBeRight()

            verify { eventPublisher.publishEvent(capture(slot)) }
            val event = slot.captured
            event.aggregateId shouldBe alumno.userId
            event.clubId shouldBe club.value
            event.planId shouldBe planId.value
            event.dia shouldBe day
            event.estado shouldBe "PARCIAL"
            event.valoracion shouldBe 3
            event.motivo shouldBe null
            event.marcaDolor shouldBe false
        }

        test("un reporte invalido no emite ningun evento ni incrementa metricas") {
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val metrics = InMemorySeguimientoMetrics()
            val command = newCommand(eventPublisher = eventPublisher, metrics = metrics)

            command
                .execute(
                    alumno,
                    day,
                    ReportStatus.NO_HECHO,
                    rating = null,
                    reason = null,
                    notes = null,
                ).shouldBeLeft()

            verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
            metrics.calls.size shouldBe 0
        }
    })
