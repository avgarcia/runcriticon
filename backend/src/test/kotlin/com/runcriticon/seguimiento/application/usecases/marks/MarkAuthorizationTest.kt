package com.runcriticon.seguimiento.application.usecases.marks

import com.runcriticon.seguimiento.application.RecordingAccessAuditor
import com.runcriticon.seguimiento.application.usecases.report.InMemoryConsentReader
import com.runcriticon.seguimiento.domain.RaceDistance
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

/**
 * Privacidad fuerte de las marcas (ADR-0002 D7): ni el ENTRENADOR ni el ADMIN pueden leer,
 * registrar ni retirar marcas — el rechazo es "side-effect-free": no toca el repositorio en ningún caso.
 * Mismo patrón que `SubmitSessionReportAuthorizationTest`.
 */
class MarkAuthorizationTest :
    FunSpec({
        val clock = MutableClock(Instant.parse("2026-08-28T18:00:00Z"))

        fun principal(role: Role) = PrincipalBuilder().role(role).build()

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role no puede listar marcas, y no se toca el repositorio") {
                val repository = InMemoryStudentMarkRepository()
                val audit = RecordingAccessAuditor()
                val query = GetMyMarksQuery(repository, audit.auditor)
                val actor = principal(role)

                query.execute(actor).shouldBeLeft(SeguimientoError.Forbidden)

                repository.upsertCalls.size shouldBe 0
                repository.deleteCalls.size shouldBe 0
                audit.shouldHaveDeniedRbac(actor, "MARCA:LIST")
            }

            test("$role no puede registrar una marca, y no se toca el repositorio") {
                val repository = InMemoryStudentMarkRepository()
                val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
                val audit = RecordingAccessAuditor()
                val command =
                    RecordMarkCommand(repository, InMemoryConsentReader(), eventPublisher, clock, audit.auditor)
                val actor = principal(role)

                command
                    .execute(actor, RaceDistance.TEN_K, timeSeconds = 2850)
                    .shouldBeLeft(SeguimientoError.Forbidden)

                repository.upsertCalls.size shouldBe 0
                audit.shouldHaveDeniedRbac(actor, "MARCA:RECORD")
            }

            test("$role no puede retirar una marca, y no se toca el repositorio") {
                val repository = InMemoryStudentMarkRepository()
                val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
                val audit = RecordingAccessAuditor()
                val command = WithdrawMarkCommand(repository, eventPublisher, clock, audit.auditor)
                val actor = principal(role)

                command
                    .execute(actor, RaceDistance.TEN_K)
                    .shouldBeLeft(SeguimientoError.Forbidden)

                repository.deleteCalls.size shouldBe 0
                audit.shouldHaveDeniedRbac(actor, "MARCA:WITHDRAW")
            }
        }
    })
