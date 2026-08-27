package com.runcriticon.planificacion.domain

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Mismas reglas que [SessionTest] (LAL-26: [SessionOverride] comparte validación intrínseca con [Session] vía
 * `ensureValidSessionContent`) — no se repite el catálogo completo, solo un caso por regla para confirmar que
 * el override las hereda de verdad.
 */
class SessionOverrideTest :
    FunSpec({
        test("un override de rodaje con distancia y ritmo absoluto se crea sin error") {
            val override =
                SessionOverride
                    .create(
                        type = SessionType.RODAJE,
                        volume = SessionVolume.Distance(meters = 6000),
                        pace = Pace.Absoluto(secondsPerKm = 300),
                        notes = "Vuelta progresiva",
                    ).shouldBeRight()

            override.type shouldBe SessionType.RODAJE
            override.volume shouldBe SessionVolume.Distance(meters = 6000)
        }

        test("un override de descanso con volumen se rechaza") {
            val error =
                SessionOverride
                    .create(type = SessionType.DESCANSO, volume = SessionVolume.Distance(meters = 1000))
                    .shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "tipo"
        }

        test("un override de descanso sin volumen ni ritmo se acepta") {
            SessionOverride.create(type = SessionType.DESCANSO).shouldBeRight()
        }

        test("un volumen de cero o negativo se rechaza") {
            val error =
                SessionOverride
                    .create(type = SessionType.RODAJE, volume = SessionVolume.Distance(meters = 0))
                    .shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "volumen"
        }

        test("unas notas de mas de 1000 caracteres se rechazan") {
            val error =
                SessionOverride
                    .create(type = SessionType.RODAJE, notes = "a".repeat(1001))
                    .shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "notas"
        }
    })
