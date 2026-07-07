package com.runcriticon.identidad.infrastructure.security

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder

/**
 * Parámetros de Argon2id y upgrade-on-login (ADR-0003 D13, LAL-58): el encoder por defecto cumple
 * el baseline OWASP (m=19 MiB, t=2, p=1) y `needsRehash` detecta hashes con parámetros anteriores.
 */
class Argon2PasswordHasherTest :
    FunSpec({
        val props = Argon2Properties()
        val encoder =
            Argon2PasswordEncoder(
                props.saltLength,
                props.hashLength,
                props.parallelism,
                props.memoryKb,
                props.iterations,
            )
        val hasher = Argon2PasswordHasher(encoder)

        test("encode produce hashes con el baseline OWASP de D13 (m=19456, t=2, p=1)") {
            hasher.encode("secreta") shouldStartWith "\$argon2id\$v=19\$m=19456,t=2,p=1\$"
        }

        test("needsRehash detecta un hash con los parámetros antiguos (defaults v5.8, 16 MiB)") {
            val legacy = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("secreta")
            hasher.needsRehash(requireNotNull(legacy)) shouldBe true
        }

        test("needsRehash es falso para un hash con los parámetros vigentes") {
            hasher.needsRehash(hasher.encode("secreta")) shouldBe false
        }

        test("matches verifica la contraseña contra su hash") {
            val hash = hasher.encode("secreta")
            hasher.matches("secreta", hash) shouldBe true
            hasher.matches("otra", hash) shouldBe false
        }
    })
