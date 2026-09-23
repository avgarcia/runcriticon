package com.runcriticon.clubtaxonomia.testing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TaxonomyBuilderTest :
    FunSpec({
        test("TaxonomyBuilder encadena withKey y withValue sobre la última key") {
            val taxonomy =
                TaxonomyBuilder()
                    .withKey("nivel")
                    .withValue("alto")
                    .withValue("medio")
                    .build()

            val nivel = taxonomy.activeKeys().single { it.label.value == "nivel" }
            nivel.values.map { it.label.value }.toSet() shouldBe setOf("alto", "medio")
        }

        test("TaxonomyBuilder soporta varias keys, cada withValue va a la última") {
            val taxonomy =
                TaxonomyBuilder()
                    .withKey("nivel")
                    .withValue("alto")
                    .withKey("terreno")
                    .withValue("asfalto")
                    .build()

            taxonomy
                .activeKeys()
                .single { it.label.value == "nivel" }
                .values
                .map { it.label.value } shouldBe
                listOf("alto")
            taxonomy
                .activeKeys()
                .single { it.label.value == "terreno" }
                .values
                .map { it.label.value } shouldBe
                listOf("asfalto")
        }
    })
