package com.runcriticon.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Prueba que [importsDeInfraestructuraEnApplication] detecta la violación real (H-5: dos listeners de
 * `auditoria` importaban una clase de `infrastructure` solo para leer su constante `QUALIFIER`) y no
 * dispara en falso para una clase de `application` que no importa nada de `infrastructure`.
 */
class LayerScanningTest :
    FunSpec({
        test("un import de infrastructure desde application es violacion") {
            val fuente =
                """
                package com.runcriticon.auditoria.application.listeners

                import com.runcriticon.auditoria.infrastructure.persistence.events.AuditoriaProcessedEventTracker
                import com.runcriticon.shared.events.ProcessedEventTracker
                """.trimIndent()
            importsDeInfraestructuraEnApplication("com.runcriticon.auditoria.application.listeners", fuente) shouldBe
                listOf("com.runcriticon.auditoria.infrastructure.persistence.events.AuditoriaProcessedEventTracker")
        }

        test("application sin import de infrastructure no es violacion") {
            val fuente =
                """
                package com.runcriticon.auditoria.application.listeners

                import com.runcriticon.shared.events.ProcessedEventTracker
                """.trimIndent()
            importsDeInfraestructuraEnApplication(
                "com.runcriticon.auditoria.application.listeners",
                fuente,
            ).shouldBeEmpty()
        }

        test("un import de infrastructure fuera de application no es violacion") {
            val fuente =
                """
                package com.runcriticon.auditoria.infrastructure.persistence.events

                import com.runcriticon.auditoria.infrastructure.other.Foo
                """.trimIndent()
            importsDeInfraestructuraEnApplication(
                "com.runcriticon.auditoria.infrastructure.persistence.events",
                fuente,
            ).shouldBeEmpty()
        }
    })
