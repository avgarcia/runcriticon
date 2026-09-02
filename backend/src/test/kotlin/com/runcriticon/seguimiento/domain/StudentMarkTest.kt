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

        test("paceSecondsPerKm en 5K y 10K, divisiones exactas") {
            StudentMark(RaceDistance.FIVE_K, timeSeconds = 1_500, modifiedAt = now).paceSecondsPerKm() shouldBe 300
            StudentMark(RaceDistance.TEN_K, timeSeconds = 2_400, modifiedAt = now).paceSecondsPerKm() shouldBe 240
        }

        test("paceSecondsPerKm en 21K y 42K redondea al segundo mas cercano (LAL-32)") {
            // 5400s / 21,097 km = 255,9805... -> 256.
            StudentMark(RaceDistance.HALF_MARATHON, timeSeconds = 5_400, modifiedAt = now)
                .paceSecondsPerKm() shouldBe 256
            // 12600s / 42,195 km = 298,6136... -> 299.
            StudentMark(RaceDistance.MARATHON, timeSeconds = 12_600, modifiedAt = now).paceSecondsPerKm() shouldBe 299
        }
    })
