package com.runcriticon.identidad.domain.user

import com.runcriticon.shared.autorizacion.model.ClubId
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class UserDeactivationTest :
    FunSpec({
        val now = Instant.parse("2026-07-02T10:00:00Z")

        fun activeUser() =
            User
                .newInvited(ClubId.of(UUID.randomUUID()), Email.of("marta@club.local"), "Marta", Role.ENTRENADOR)
                .activate("hash-argon2", now)

        test("deactivate pasa una cuenta ACTIVO a DESACTIVADO sin tocar la contraseña") {
            val deactivated = activeUser().deactivate(now)

            deactivated.status shouldBe UserStatus.DESACTIVADO
            deactivated.isActive() shouldBe false
            deactivated.passwordHash shouldBe "hash-argon2"
        }

        test("deactivate sobre una cuenta que no está ACTIVO es una violación de invariante") {
            val deactivated = activeUser().deactivate(now)

            shouldThrow<IllegalArgumentException> { deactivated.deactivate(now) }
        }

        test("deactivate sobre una cuenta INVITADO es una violación de invariante") {
            val invited =
                User.newInvited(
                    ClubId.of(UUID.randomUUID()),
                    Email.of("nuevo@club.local"),
                    "Nuevo",
                    Role.ALUMNO,
                )

            shouldThrow<IllegalArgumentException> { invited.deactivate(now) }
        }
    })
