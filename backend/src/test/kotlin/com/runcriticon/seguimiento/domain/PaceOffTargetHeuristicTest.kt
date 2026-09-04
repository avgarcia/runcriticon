package com.runcriticon.seguimiento.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PaceOffTargetHeuristicTest :
    FunSpec({
        test("una nota que dice ir por encima del objetivo dispara la heuristica") {
            matchesPaceOffTargetHeuristic("Fui a tope, iba por encima del ritmo previsto") shouldBe true
        }

        test("mas rapido sin tilde tambien dispara la heuristica") {
            matchesPaceOffTargetHeuristic("Note que iba mas rapido de lo normal") shouldBe true
        }

        test("mas lento dispara la heuristica") {
            matchesPaceOffTargetHeuristic("Hoy iba más lento, cansado de la semana") shouldBe true
        }

        test("mayusculas no impiden la deteccion") {
            matchesPaceOffTargetHeuristic("FUI POR ENCIMA todo el rato") shouldBe true
        }

        test("una nota sin ninguna de las frases no dispara la heuristica") {
            matchesPaceOffTargetHeuristic("Buena sesion, me senti comodo") shouldBe false
        }

        test("una nota vacia no dispara la heuristica") {
            matchesPaceOffTargetHeuristic("") shouldBe false
        }
    })
