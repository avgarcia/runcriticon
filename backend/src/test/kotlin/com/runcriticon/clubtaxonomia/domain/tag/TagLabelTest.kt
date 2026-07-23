package com.runcriticon.clubtaxonomia.domain.tag

import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TagLabelTest :
    FunSpec({
        test("guarda el literal recortado como forma de visualización") {
            TagLabel.of("  Nivel  ", 40, "nombre").shouldBeRight().value shouldBe "Nivel"
        }

        test("normaliza ignorando mayúsculas, acentos y espacios de los extremos") {
            TagLabel.of("Nivel", 40, "nombre").shouldBeRight().normalized shouldBe "nivel"
            TagLabel.of("nivel ", 40, "nombre").shouldBeRight().normalized shouldBe "nivel"
            TagLabel.of("Nível", 40, "nombre").shouldBeRight().normalized shouldBe "nivel"
        }

        test("lowercase usa Locale.ROOT y no depende del locale del sistema") {
            TagLabel.of("NIVEL", 40, "nombre").shouldBeRight().normalized shouldBe "nivel"
        }

        test("un nombre en blanco tras trim devuelve InvalidInput blank") {
            TagLabel.of("   ", 40, "nombre").shouldBeLeft(ClubTaxonomiaError.InvalidInput("nombre", "blank"))
        }

        test("un nombre más largo que el máximo devuelve too_long; el máximo exacto es válido") {
            TagLabel
                .of("a".repeat(41), 40, "nombre")
                .shouldBeLeft(ClubTaxonomiaError.InvalidInput("nombre", "too_long"))
            TagLabel.of("a".repeat(40), 40, "nombre").shouldBeRight().value shouldBe "a".repeat(40)
        }

        test("los espacios internos no se colapsan (paridad con trim de PostgreSQL)") {
            val doble = TagLabel.of("nivel  alto", 40, "nombre").shouldBeRight()
            val simple = TagLabel.of("nivel alto", 40, "nombre").shouldBeRight()
            doble.normalized shouldNotBe simple.normalized
        }

        test("la tabla NORMALIZATION_CASES es coherente con normalized") {
            NORMALIZATION_CASES.forEach { (raw, expected) ->
                TagLabel.of(raw, 60, "valor").shouldBeRight().normalized shouldBe expected
            }
        }
    }) {
    companion object {
        /**
         * Pares (literal tecleado, forma normalizada) reutilizables por el test de integración que ejecutará
         * `unaccent(lower(trim(x)))` contra PostgreSQL real (Testcontainers) y comparará con esta misma tabla para
         * garantizar la paridad dominio ↔ índice único.
         */
        val NORMALIZATION_CASES =
            listOf(
                "Nivel" to "nivel",
                "nivel " to "nivel",
                "Nível" to "nivel",
                "  MARATÓN  " to "maraton",
                "Ritmo Fácil" to "ritmo facil",
            )
    }
}
