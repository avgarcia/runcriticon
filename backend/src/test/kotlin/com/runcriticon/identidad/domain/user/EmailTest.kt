package com.runcriticon.identidad.domain.user

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EmailTest :
    FunSpec({
        test("normaliza a minúsculas y recorta espacios (ADR-0003 D2)") {
            Email.of("  Admin@Runcriticon.LOCAL  ").value shouldBe "admin@runcriticon.local"
        }
    })
