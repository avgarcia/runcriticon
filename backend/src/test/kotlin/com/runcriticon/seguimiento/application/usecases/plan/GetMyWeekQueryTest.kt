package com.runcriticon.seguimiento.application.usecases.plan

import com.github.f4b6a3.uuid.UuidCreator
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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class GetMyWeekQueryTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val alumno = Principal(userId = UuidCreator.getTimeOrderedEpoch(), clubId = club.value, role = Role.ALUMNO)
        val session = ResolvedSession(day = LocalDate.parse("2026-08-17"), type = SessionType.RODAJE)

        test("devuelve lo que resuelve el lector para el lunes pedido") {
            val reader = InMemoryResolvedPlanReader(listOf(session))
            val query = GetMyWeekQuery(reader, MutableClock(Instant.parse("2026-08-19T10:00:00Z")))

            val result = query.execute(alumno, LocalDate.parse("2026-08-17")).shouldBeRight()

            result.week shouldBe LocalDate.parse("2026-08-17")
            result.sessions shouldBe listOf(session)
        }

        test("opera sobre el club y el alumno del actor, nunca sobre un id de entrada") {
            val reader = InMemoryResolvedPlanReader()
            val query = GetMyWeekQuery(reader, MutableClock(Instant.parse("2026-08-19T10:00:00Z")))

            query.execute(alumno, LocalDate.parse("2026-08-17"))

            val call = reader.calls.single()
            call.clubId shouldBe club
            call.studentId.value shouldBe alumno.userId
            call.from shouldBe LocalDate.parse("2026-08-17")
            call.to shouldBe LocalDate.parse("2026-08-23")
        }

        test("sin semana usa el lunes de la semana en curso en la zona del club") {
            val reader = InMemoryResolvedPlanReader()
            // Miércoles 2026-08-19 10:00 UTC -> miércoles en Madrid también (CEST, UTC+2): lunes de esa semana
            // es el 17.
            val query = GetMyWeekQuery(reader, MutableClock(Instant.parse("2026-08-19T10:00:00Z")))

            val result = query.execute(alumno).shouldBeRight()

            result.week shouldBe LocalDate.parse("2026-08-17")
        }

        test("cerca de medianoche en Madrid ya es lunes aunque en UTC siga siendo domingo") {
            val reader = InMemoryResolvedPlanReader()
            // 2026-08-16T22:30:00Z es domingo en UTC, pero ya lunes 2026-08-17T00:30 en Madrid (CEST, UTC+2).
            val query = GetMyWeekQuery(reader, MutableClock(Instant.parse("2026-08-16T22:30:00Z")))

            val result = query.execute(alumno).shouldBeRight()

            result.week shouldBe LocalDate.parse("2026-08-17")
        }

        test("una semana que no es lunes es InvalidInput y no toca el lector") {
            val reader = InMemoryResolvedPlanReader()
            val query = GetMyWeekQuery(reader, MutableClock(Instant.parse("2026-08-19T10:00:00Z")))

            query
                .execute(alumno, LocalDate.parse("2026-08-18"))
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "semana", reason = "week_not_monday"))

            reader.calls.size shouldBe 0
        }
    })
