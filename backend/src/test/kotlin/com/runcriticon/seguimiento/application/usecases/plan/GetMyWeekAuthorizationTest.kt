package com.runcriticon.seguimiento.application.usecases.plan

import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.testing.MutableClock
import com.runcriticon.testing.PrincipalBuilder
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

/** Solo el ALUMNO ve su propia semana resuelta; el rechazo no toca el lector. */
class GetMyWeekAuthorizationTest :
    FunSpec({
        val clock = MutableClock(Instant.parse("2026-08-19T10:00:00Z"))

        fun principal(role: Role) = PrincipalBuilder().role(role).build()

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
