package com.runcriticon.identidad.domain.user

import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.util.UUID

class UserDeletionTest :
    FunSpec({
        val now = Instant.parse("2026-11-04T10:00:00Z")

        fun invitedUser() =
            User.newInvited(ClubId.of(UUID.randomUUID()), Email.of("marta@club.local"), "Marta", Role.ALUMNO)

        fun userIn(status: UserStatus): User =
            when (status) {
                UserStatus.INVITADO -> invitedUser()
                UserStatus.ACTIVO -> invitedUser().activate("hash-argon2", now)
                UserStatus.DESACTIVADO -> invitedUser().activate("hash-argon2", now).deactivate(now)
                UserStatus.BAJA -> invitedUser().markForDeletion(now)
            }

        listOf(UserStatus.INVITADO, UserStatus.ACTIVO, UserStatus.DESACTIVADO).forEach { previous ->
            test("marcar la baja de una cuenta $previous guarda su estado y cancelarla lo restaura") {
                val marked = userIn(previous).markForDeletion(now)

                marked.status shouldBe UserStatus.BAJA
                marked.isActive() shouldBe false
                marked.markedForDeletionAt shouldBe now
                marked.statusBeforeDeletion shouldBe previous

                val cancelled = marked.cancelDeletion()

                cancelled.status shouldBe previous
                cancelled.markedForDeletionAt shouldBe null
                cancelled.statusBeforeDeletion shouldBe null
            }
        }

        test("marcar la baja de una cuenta que ya está en BAJA es una violación de invariante") {
            shouldThrow<IllegalArgumentException> { userIn(UserStatus.BAJA).markForDeletion(now) }
        }

        test("cancelar la baja de una cuenta que no está en BAJA es una violación de invariante") {
            shouldThrow<IllegalArgumentException> { userIn(UserStatus.ACTIVO).cancelDeletion() }
        }

        test("una cuenta en BAJA no se desactiva ni cambia de contraseña") {
            val marked = userIn(UserStatus.ACTIVO).markForDeletion(now)

            shouldThrow<IllegalArgumentException> { marked.deactivate(now) }
            shouldThrow<IllegalArgumentException> { marked.changePassword("otro-hash", now) }
        }

        test("la gracia vence pasados 30 días desde que se marcó la baja, no antes") {
            val marked = userIn(UserStatus.ACTIVO).markForDeletion(now)

            marked.isDeletionGraceExpired(now.plus(Duration.ofDays(29))) shouldBe false
            marked.isDeletionGraceExpired(now.plus(User.DELETION_GRACE)) shouldBe false
            marked.isDeletionGraceExpired(now.plus(Duration.ofDays(31))) shouldBe true
        }

        test("una cuenta fuera de BAJA nunca tiene la gracia vencida") {
            userIn(UserStatus.ACTIVO).isDeletionGraceExpired(now.plus(Duration.ofDays(365))) shouldBe false
        }

        test("el agregado rechaza un estado de baja incoherente") {
            val active = userIn(UserStatus.ACTIVO)

            shouldThrow<IllegalArgumentException> { active.copy(status = UserStatus.BAJA) }
            shouldThrow<IllegalArgumentException> { active.copy(markedForDeletionAt = now) }
            shouldThrow<IllegalArgumentException> {
                active.copy(
                    status = UserStatus.BAJA,
                    markedForDeletionAt = now,
                    statusBeforeDeletion = UserStatus.BAJA,
                )
            }
        }
    })
