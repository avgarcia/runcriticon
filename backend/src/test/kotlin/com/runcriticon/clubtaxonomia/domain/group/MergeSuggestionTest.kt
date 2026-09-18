package com.runcriticon.clubtaxonomia.domain.group

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class MergeSuggestionTest :
    FunSpec({
        val now = Instant.parse("2026-08-25T10:00:00Z")
        val low = GroupId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val high = GroupId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"))

        test("micro construye una sugerencia con el mismo grupo en ambos lados") {
            val suggestion = MergeSuggestion.micro(low, now)

            suggestion.groupIdA shouldBe low
            suggestion.groupIdB shouldBe low
            suggestion.type shouldBe MergeSuggestionType.MICRO
        }

        test("duplicateOf ordena el par sin importar en que orden llegan los ids") {
            val fromLowHigh = MergeSuggestion.duplicateOf(low, high, now)
            val fromHighLow = MergeSuggestion.duplicateOf(high, low, now)

            fromLowHigh.groupIdA shouldBe low
            fromLowHigh.groupIdB shouldBe high
            fromHighLow.groupIdA shouldBe low
            fromHighLow.groupIdB shouldBe high
        }

        test("canonicalPair da el mismo orden que duplicateOf") {
            val pair = MergeSuggestion.canonicalPair(high, low)

            pair.first shouldBe low
            pair.second shouldBe high
        }

        test("una sugerencia DUPLICADO con el orden invertido a mano viola la invariante") {
            shouldThrow<IllegalArgumentException> {
                MergeSuggestion(high, low, MergeSuggestionType.DUPLICADO, now)
            }
        }

        test("una sugerencia MICRO con dos grupos distintos viola la invariante") {
            shouldThrow<IllegalArgumentException> {
                MergeSuggestion(low, high, MergeSuggestionType.MICRO, now)
            }
        }
    })
