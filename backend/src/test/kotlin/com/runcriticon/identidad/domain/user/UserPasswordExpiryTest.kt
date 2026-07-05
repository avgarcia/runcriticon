package com.runcriticon.identidad.domain.user

import com.runcriticon.shared.autorizacion.model.ClubId
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.util.UUID

class UserPasswordExpiryTest :
    FunSpec({
        val now = Instant.parse("2026-06-26T10:00:00Z")

        fun active(
            passwordHash: String? = "hash",
            passwordUpdatedAt: Instant? = now,
        ) = User(
            id = UserId.new(),
            clubId = ClubId.of(UUID.randomUUID()),
            email = Email.of("ana@club.local"),
            name = "Ana",
            role = Role.ALUMNO,
            passwordHash = passwordHash,
            status = UserStatus.ACTIVO,
            passwordUpdatedAt = passwordUpdatedAt,
        )

        test("contraseña fijada hace menos de 90 días no está caducada") {
            active(passwordUpdatedAt = now.minus(Duration.ofDays(89))).isPasswordExpired(now) shouldBe false
        }

        test("contraseña fijada hace más de 90 días está caducada") {
            active(passwordUpdatedAt = now.minus(Duration.ofDays(91))).isPasswordExpired(now) shouldBe true
        }

        test("exactamente 90 días no se considera caducada (límite inclusivo)") {
            active(passwordUpdatedAt = now.minus(Duration.ofDays(90))).isPasswordExpired(now) shouldBe false
        }

        test("sin passwordUpdatedAt no caduca (un dato ausente no bloquea el acceso)") {
            active(passwordUpdatedAt = null).isPasswordExpired(now) shouldBe false
        }

        test("cuenta solo-magic-link (sin hash) no caduca") {
            active(passwordHash = null, passwordUpdatedAt = now.minus(Duration.ofDays(365)))
                .isPasswordExpired(now) shouldBe false
        }

        test("changePassword fija el hash nuevo y reinicia el reloj de caducidad") {
            val later = now.plus(Duration.ofDays(120))
            val changed =
                active(passwordUpdatedAt = now.minus(Duration.ofDays(120))).changePassword("nuevo-hash", later)

            changed.passwordHash shouldBe "nuevo-hash"
            changed.passwordUpdatedAt shouldBe later
            changed.isPasswordExpired(later) shouldBe false
        }

        test("changePassword sobre una cuenta no activa es violación de invariante") {
            val invited = User.newInvited(ClubId.of(UUID.randomUUID()), Email.of("ana@club.local"), "Ana", Role.ALUMNO)
            shouldThrow<IllegalArgumentException> { invited.changePassword("hash", now) }
        }
    })
