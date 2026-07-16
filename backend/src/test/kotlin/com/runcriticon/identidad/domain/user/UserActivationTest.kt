package com.runcriticon.identidad.domain.user

import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class UserActivationTest :
    FunSpec({
        val now = Instant.parse("2026-06-26T10:00:00Z")
        val invited = User.newInvited(ClubId.of(UUID.randomUUID()), Email.of("marta@club.local"), "Marta", Role.ALUMNO)

        test("activate fija la contraseña, anota la fecha y pasa la cuenta a ACTIVO") {
            val activated = invited.activate("hash-argon2", now)

            activated.status shouldBe UserStatus.ACTIVO
            activated.passwordHash shouldBe "hash-argon2"
            activated.passwordUpdatedAt shouldBe now
            activated.isActive() shouldBe true
        }

        test("activate sobre una cuenta que no está INVITADO es una violación de invariante") {
            val active = invited.activate("hash-argon2", now)

            shouldThrow<IllegalArgumentException> { active.activate("otro-hash", now) }
        }
    })
