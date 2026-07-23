package com.runcriticon.clubtaxonomia.domain.tag

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DistanceTest :
    FunSpec({
        test("los códigos persistidos son 5K/10K/21K/42K (contrato con eventos y JSONB)") {
            Distance.entries.map { it.code } shouldBe listOf("5K", "10K", "21K", "42K")
        }

        test("fromCode es la inversa de code para todas las distancias") {
            Distance.entries.forEach { Distance.fromCode(it.code) shouldBe it }
        }

        test("fromCode de un código desconocido devuelve null") {
            Distance.fromCode("50K") shouldBe null
        }

        test("hay exactamente cuatro distancias estándar") {
            Distance.entries.size shouldBe 4
        }
    })
