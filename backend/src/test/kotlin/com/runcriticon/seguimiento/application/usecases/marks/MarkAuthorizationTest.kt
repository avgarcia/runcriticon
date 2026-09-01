package com.runcriticon.seguimiento.application.usecases.marks

import com.runcriticon.seguimiento.domain.RaceDistance
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
import java.util.UUID

/**
 * Privacidad fuerte de las marcas (LAL-31, AC2, ADR-0002 D7): ni el ENTRENADOR ni el ADMIN pueden leer,
 * registrar ni retirar marcas — el rechazo es "side-effect-free": no toca el repositorio en ningún caso.
 * Mismo patrón que `SubmitSessionReportAuthorizationTest`.
 */
class MarkAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val clock = MutableClock(Instant.parse("2026-08-28T18:00:00Z"))

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role no puede listar marcas, y no se toca el repositorio") {
                val repository = InMemoryStudentMarkRepository()
                val query = GetMyMarksQuery(repository)

                query.execute(principal(role)).shouldBeLeft(SeguimientoError.Forbidden)

                repository.upsertCalls.size shouldBe 0
                repository.deleteCalls.size shouldBe 0
            }

            test("$role no puede registrar una marca, y no se toca el repositorio") {
                val repository = InMemoryStudentMarkRepository()
                val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
                val command = RecordMarkCommand(repository, eventPublisher, clock)

                command
                    .execute(principal(role), RaceDistance.TEN_K, timeSeconds = 2850)
                    .shouldBeLeft(SeguimientoError.Forbidden)

                repository.upsertCalls.size shouldBe 0
            }

            test("$role no puede retirar una marca, y no se toca el repositorio") {
                val repository = InMemoryStudentMarkRepository()
                val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
                val command = WithdrawMarkCommand(repository, eventPublisher, clock)

                command
                    .execute(principal(role), RaceDistance.TEN_K)
                    .shouldBeLeft(SeguimientoError.Forbidden)

                repository.deleteCalls.size shouldBe 0
            }
        }
    })
