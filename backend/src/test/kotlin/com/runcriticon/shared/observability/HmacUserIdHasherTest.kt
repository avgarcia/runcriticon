package com.runcriticon.shared.observability

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import java.util.UUID

class HmacUserIdHasherTest :
    FunSpec({
        val hasher = HmacUserIdHasher("test-salt-not-prod")

        test("el hash es estable para el mismo userId") {
            val userId = UUID.randomUUID()
            hasher.hash(userId) shouldBe hasher.hash(userId)
        }

        test("usuarios distintos producen hashes distintos") {
            hasher.hash(UUID.randomUUID()) shouldNotBe hasher.hash(UUID.randomUUID())
        }

        test("el hash nunca contiene el userId en claro") {
            val userId = UUID.randomUUID()
            hasher.hash(userId) shouldNotContain userId.toString()
        }

        test("falla al arrancar si el salt esta en blanco") {
            shouldThrow<IllegalArgumentException> { HmacUserIdHasher("") }
        }
    })
