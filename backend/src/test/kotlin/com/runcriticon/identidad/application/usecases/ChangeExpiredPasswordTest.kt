package com.runcriticon.identidad.application.usecases

import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.application.PasswordPolicy
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.PasswordHistory
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.SessionRevoker
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ChangeExpiredPasswordTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val userId = UserId.new()
        val newPassword = "clave-nueva-larga"

        val userRepository = mockk<UserRepository>(relaxed = true)
        val passwordHasher = mockk<PasswordHasher>()
        val passwordPolicy = mockk<PasswordPolicy>()
        val passwordHistory = mockk<PasswordHistory>(relaxed = true)
        val sessionRevoker = mockk<SessionRevoker>(relaxed = true)
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val useCase =
            ChangeExpiredPassword(
                userRepository,
                passwordHasher,
                passwordPolicy,
                passwordHistory,
                sessionRevoker,
                auditTrail,
            )

        fun user(
            status: UserStatus = UserStatus.ACTIVO,
            passwordHash: String? = "hash-actual",
            passwordUpdatedAt: Instant? = Instant.now().minus(Duration.ofDays(120)),
        ) = User(
            id = userId,
            clubId = club,
            email = Email.of("ana@club.local"),
            name = "Ana",
            role = Role.ENTRENADOR,
            passwordHash = passwordHash,
            status = status,
            passwordUpdatedAt = passwordUpdatedAt,
        )

        beforeTest {
            clearMocks(userRepository, passwordHasher, passwordPolicy, passwordHistory, sessionRevoker, auditTrail)
            every { userRepository.findByEmail(club, any()) } returns user()
            every { passwordHasher.matches(any(), any()) } returns true
            every { passwordPolicy.validate(any(), any()) } returns Unit.right()
            every { passwordHasher.encode(newPassword) } returns "encoded-nuevo"
        }

        test("cambio correcto: fija hash nuevo, reinicia reloj, histórico, auditoría y Principal") {
            val userSlot = slot<User>()
            val auditSlot = slot<AuditEntry>()
            every { userRepository.save(capture(userSlot)) } returns Unit
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            val principal =
                useCase.execute(club, "ana@club.local", "clave-actual", newPassword).shouldBeRight()

            userSlot.captured.passwordHash shouldBe "encoded-nuevo"
            userSlot.captured.status shouldBe UserStatus.ACTIVO
            userSlot.captured.isPasswordExpired(Instant.now()) shouldBe false
            principal.userId shouldBe userId.value
            principal.role shouldBe Role.ENTRENADOR

            verify { passwordHistory.record(userId, club, "encoded-nuevo", any()) }
            verify { sessionRevoker.revokeAll(userId.value) }
            auditSlot.captured.type shouldBe AuditEventType.PASSWORD_CAMBIADA
            auditSlot.captured.subjectId shouldBe userId.value
        }

        test("contraseña actual incorrecta devuelve InvalidCredentials y no cambia nada") {
            every { passwordHasher.matches(any(), any()) } returns false

            useCase
                .execute(club, "ana@club.local", "mala", newPassword)
                .shouldBeLeft(IdentidadError.InvalidCredentials)

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
        }

        test("cuenta no activa devuelve AccountNotActive") {
            every { userRepository.findByEmail(club, any()) } returns user(status = UserStatus.INVITADO)

            useCase
                .execute(club, "ana@club.local", "clave-actual", newPassword)
                .shouldBeLeft(IdentidadError.AccountNotActive)

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
        }

        test("contraseña no caducada devuelve Conflict (este endpoint solo atiende el cambio forzado)") {
            every { userRepository.findByEmail(club, any()) } returns user(passwordUpdatedAt = Instant.now())

            useCase
                .execute(club, "ana@club.local", "clave-actual", newPassword)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
        }

        test("nueva contraseña que incumple la política D6 devuelve InvalidInput(password)") {
            every { passwordPolicy.validate(any(), any()) } returns
                IdentidadError.InvalidInput("password", "reused").left()

            val error =
                useCase
                    .execute(club, "ana@club.local", "clave-actual", newPassword)
                    .shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.InvalidInput>()
            error.field shouldBe "password"

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
        }
    })
