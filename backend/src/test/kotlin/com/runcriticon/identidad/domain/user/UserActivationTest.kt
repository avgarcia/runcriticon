package com.runcriticon.identidad.domain.user

import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class UserActivationTest :
    FunSpec({
        val invited = User.newInvited(UUID.randomUUID(), Email.of("marta@club.local"), "Marta", Role.ALUMNO)

        test("activate fija la contraseña y pasa la cuenta a ACTIVO") {
            val activated = invited.activate("hash-argon2")

            activated.status shouldBe UserStatus.ACTIVO
            activated.passwordHash shouldBe "hash-argon2"
            activated.isActive() shouldBe true
        }

        test("activate sobre una cuenta que no está INVITADO es una violación de invariante") {
            val active = invited.activate("hash-argon2")

            shouldThrow<IllegalArgumentException> { active.activate("otro-hash") }
        }
    })
