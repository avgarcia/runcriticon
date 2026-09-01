package com.runcriticon.seguimiento.domain

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class StudentMarkTest :
    FunSpec({
        val now = Instant.parse("2026-08-17T18:00:00Z")

        test("un tiempo positivo es valido") {
            val mark = StudentMark.create(RaceDistance.TEN_K, timeSeconds = 2850, modifiedAt = now).shouldBeRight()

            mark.distance shouldBe RaceDistance.TEN_K
            mark.timeSeconds shouldBe 2850
        }

        test("un tiempo de mas de una hora es valido, sin tope superior") {
            StudentMark.create(RaceDistance.MARATHON, timeSeconds = 12600, modifiedAt = now).shouldBeRight()
        }

        test("tiempo cero es InvalidInput") {
            StudentMark
                .create(RaceDistance.FIVE_K, timeSeconds = 0, modifiedAt = now)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "tiempoSegundos", reason = "not_positive"))
        }

        test("tiempo negativo es InvalidInput") {
            StudentMark
                .create(RaceDistance.FIVE_K, timeSeconds = -1, modifiedAt = now)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "tiempoSegundos", reason = "not_positive"))
        }
    })
