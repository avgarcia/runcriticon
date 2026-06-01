package com.runcriticon

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Smoke test del stack de testing (ADR-0010): demuestra que JUnit 5 + Kotest + las
 * assertions de Arrow funcionan end-to-end en el build. No prueba lógica de negocio
 * (no hay aún): valida que el andamiaje compila y corre.
 *
 * El patrón Either<Error, T> de Arrow (ADR-0008 D11) es el que usarán los agregados
 * y casos de uso. Aquí se ejercita en su forma más simple.
 */
class StackSmokeTest : FunSpec({

    test("Kotest corre y las assertions básicas funcionan") {
        (2 + 2) shouldBe 4
    }

    test("Either.Right se reconoce con las assertions de Arrow") {
        val resultado: Either<String, Int> = 42.right()
        resultado.shouldBeRight(42)
    }

    test("Either.Left se reconoce con las assertions de Arrow") {
        val resultado: Either<String, Int> = "forbidden".left()
        resultado.shouldBeLeft("forbidden")
    }
})
