package com.runcriticon.seguimiento.application.usecases.report

import com.runcriticon.seguimiento.application.usecases.plan.InMemoryResolvedPlanReader
import com.runcriticon.seguimiento.domain.ReportStatus
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.MutableClock
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Solo el ALUMNO reporta sus sesiones; el rechazo no toca ni el lector ni el repositorio (LAL-30). */
class SubmitSessionReportAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val clock = MutableClock(Instant.parse("2026-08-17T18:00:00Z"))

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role no puede reportar una sesion, y no se toca ni el lector ni el repositorio") {
                val reader = InMemoryResolvedPlanReader()
                val repository = InMemorySessionReportRepository()
                val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
                val metrics = InMemorySeguimientoMetrics()
                val command = SubmitSessionReportCommand(reader, repository, eventPublisher, metrics, clock)

                command
                    .execute(
                        principal(role),
                        LocalDate.parse("2026-08-17"),
                        ReportStatus.HECHO,
                        rating = 4,
                        reason = null,
                        notes = null,
                    ).shouldBeLeft(SeguimientoError.Forbidden)

                reader.calls.size shouldBe 0
                reader.dayCalls.size shouldBe 0
                repository.calls.size shouldBe 0
                metrics.calls.size shouldBe 0
            }
        }
    })
