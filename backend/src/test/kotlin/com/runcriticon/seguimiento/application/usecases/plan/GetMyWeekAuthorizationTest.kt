package com.runcriticon.seguimiento.application.usecases.plan

import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.MutableClock
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

/** Solo el ALUMNO ve su propia semana resuelta; el rechazo no toca el lector (LAL-29). */
class GetMyWeekAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val clock = MutableClock(Instant.parse("2026-08-19T10:00:00Z"))

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        test("el ALUMNO puede ver su semana") {
            GetMyWeekQuery(InMemoryResolvedPlanReader(), clock)
                .execute(principal(Role.ALUMNO))
                .shouldBeRight()
        }

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role no puede ver la semana de un alumno, y no se toca el lector") {
                val reader = InMemoryResolvedPlanReader()

                GetMyWeekQuery(reader, clock)
                    .execute(principal(role))
                    .shouldBeLeft(SeguimientoError.Forbidden)

                reader.calls.size shouldBe 0
            }
        }
    })
