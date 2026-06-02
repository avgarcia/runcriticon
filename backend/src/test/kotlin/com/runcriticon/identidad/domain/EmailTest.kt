package com.runcriticon.identidad.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EmailTest :
    FunSpec({
        test("normaliza a minúsculas y recorta espacios (ADR-0003 D2)") {
            Email.de("  Admin@Runcriticon.LOCAL  ").valor shouldBe "admin@runcriticon.local"
        }
    })
