package com.runcriticon.clubtaxonomia.domain.group

import com.runcriticon.clubtaxonomia.domain.person.PersonId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.util.UUID

class MergeSuggestionCalculatorTest :
    FunSpec({
        fun members(count: Int): Set<PersonId> = (1..count).map { PersonId.of(UUID.randomUUID()) }.toSet()

        test("un grupo vacio es micro") {
            MergeSuggestionCalculator.isMicro(emptySet()) shouldBe true
        }

        test("un grupo con exactamente el umbral de alumnos es micro") {
            MergeSuggestionCalculator.isMicro(members(MergeSuggestionCalculator.MICRO_THRESHOLD)) shouldBe true
        }

        test("un grupo con uno mas que el umbral no es micro") {
            MergeSuggestionCalculator.isMicro(members(MergeSuggestionCalculator.MICRO_THRESHOLD + 1)) shouldBe false
        }

        test("dos grupos identicos solapan al 100 porciento") {
            val shared = members(5)

            MergeSuggestionCalculator.overlapRatio(shared, shared) shouldBe (1.0 plusOrMinus 0.0001)
        }

        test("dos grupos disjuntos no solapan") {
            MergeSuggestionCalculator.overlapRatio(members(3), members(3)) shouldBe (0.0 plusOrMinus 0.0001)
        }

        test("dos grupos vacios no cuentan como duplicados: la union vacia da cero, no NaN") {
            MergeSuggestionCalculator.overlapRatio(emptySet(), emptySet()) shouldBe (0.0 plusOrMinus 0.0001)
            MergeSuggestionCalculator.isDuplicate(emptySet(), emptySet()) shouldBe false
        }

        test("el indice de Jaccard es simetrico") {
            val a = members(4)
            val bBase = a.take(3).toSet()
            val b = bBase + PersonId.of(UUID.randomUUID())

            MergeSuggestionCalculator.overlapRatio(a, b) shouldBe MergeSuggestionCalculator.overlapRatio(b, a)
        }

        test("un solape justo en el umbral del 80 porciento es duplicado") {
            // 4 comunes de 5 en total (union) = 0.8 exacto.
            val common = members(4)
            val a = common + PersonId.of(UUID.randomUUID())
            val b = common

            MergeSuggestionCalculator.overlapRatio(a, b) shouldBe (0.8 plusOrMinus 0.0001)
            MergeSuggestionCalculator.isDuplicate(a, b) shouldBe true
        }

        test("un solape justo por debajo del umbral no es duplicado") {
            // 3 comunes de 5 en total (union) = 0.6.
            val common = members(3)
            val a = common + members(1)
            val b = common + members(1)

            MergeSuggestionCalculator.overlapRatio(a, b) shouldBe (0.6 plusOrMinus 0.0001)
            MergeSuggestionCalculator.isDuplicate(a, b) shouldBe false
        }

        test("un grupo pequeno anidado dentro de uno grande no es duplicado (Jaccard, no contencion)") {
            val small = members(2)
            val big = small + members(48)

            MergeSuggestionCalculator.isDuplicate(small, big) shouldBe false
        }
    })
