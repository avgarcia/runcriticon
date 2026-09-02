package com.runcriticon.seguimiento.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

class ResolvedPaceTest :
    FunSpec({
        val now = Instant.parse("2026-08-17T18:00:00Z")
        val markTenK = StudentMark(RaceDistance.TEN_K, timeSeconds = 2_400, modifiedAt = now) // 240 s/km

        test("delta positivo suma sobre el pace de la marca") {
            val resolved = resolveRelativePace(RaceDistance.TEN_K, deltaSecondsPerKm = 10, mark = markTenK)

            resolved shouldBe ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = 10, secondsPerKm = 250)
        }

        test("delta negativo resta sobre el pace de la marca") {
            val resolved = resolveRelativePace(RaceDistance.TEN_K, deltaSecondsPerKm = -15, mark = markTenK)

            resolved.secondsPerKm shouldBe 225
        }

        test("sin marca el ritmo queda sin resolver, aunque el delta sea conocido") {
            val resolved = resolveRelativePace(RaceDistance.TEN_K, deltaSecondsPerKm = 10, mark = null)

            resolved.secondsPerKm.shouldBeNull()
            resolved.deltaSecondsPerKm shouldBe 10
        }

        test("un delta absurdamente negativo no baja del suelo de 1 s/km") {
            val resolved = resolveRelativePace(RaceDistance.TEN_K, deltaSecondsPerKm = -1_000, mark = markTenK)

            resolved.secondsPerKm shouldBe 1
        }

        test("Relative resuelto sin delta viola el invariante y falla al construirse a mano") {
            shouldThrow<IllegalArgumentException> {
                ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = null, secondsPerKm = 250)
            }
        }

        test("Relative sin resolver (delta null, secondsPerKm null) es valido — fila legacy pre-LAL-32") {
            val legacy = ResolvedPace.Relative(RaceDistance.TEN_K, deltaSecondsPerKm = null, secondsPerKm = null)

            legacy.secondsPerKm.shouldBeNull()
        }
    })
