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
            TagLabel.forKey("  Nivel  ").shouldBeRight().value shouldBe "Nivel"
        }

        test("normaliza ignorando mayúsculas, acentos y espacios de los extremos") {
            TagLabel.forKey("Nivel").shouldBeRight().normalized shouldBe "nivel"
            TagLabel.forKey("nivel ").shouldBeRight().normalized shouldBe "nivel"
            TagLabel.forKey("Nível").shouldBeRight().normalized shouldBe "nivel"
        }

        test("lowercase usa Locale.ROOT y no depende del locale del sistema") {
            TagLabel.forKey("NIVEL").shouldBeRight().normalized shouldBe "nivel"
        }

        test("un nombre en blanco tras trim devuelve InvalidInput blank") {
            TagLabel.forKey("   ").shouldBeLeft(ClubTaxonomiaError.InvalidInput("nombre", "blank"))
        }

        test("cada fábrica aplica su propio límite y su propio nombre de campo") {
            val over = "a".repeat(TagKey.MAX_LABEL_LENGTH + 1)
            TagLabel.forKey(over).shouldBeLeft(ClubTaxonomiaError.InvalidInput("nombre", "too_long"))
            // El mismo literal es válido como valor: su límite es mayor.
            TagLabel.forValue(over).shouldBeRight().value shouldBe over
            TagLabel
                .forValue("a".repeat(TagValue.MAX_LABEL_LENGTH + 1))
                .shouldBeLeft(ClubTaxonomiaError.InvalidInput("valor", "too_long"))
            // El máximo exacto es válido.
            TagLabel.forKey("a".repeat(TagKey.MAX_LABEL_LENGTH)).shouldBeRight()
            TagLabel.forValue("a".repeat(TagValue.MAX_LABEL_LENGTH)).shouldBeRight()
        }

        test("los espacios internos no se colapsan (paridad con trim de PostgreSQL)") {
            val doble = TagLabel.forKey("nivel  alto").shouldBeRight()
            val simple = TagLabel.forKey("nivel alto").shouldBeRight()
            doble.normalized shouldNotBe simple.normalized
        }

        test("la tabla NORMALIZATION_CASES es coherente con normalized") {
            NORMALIZATION_CASES.forEach { (raw, expected) ->
                TagLabel.forValue(raw).shouldBeRight().normalized shouldBe expected
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
