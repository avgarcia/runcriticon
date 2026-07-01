package com.runcriticon.identidad.application.usecases

import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.application.PasswordPolicy
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.MagicLinkRepository
import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.PasswordHistory
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.magiclink.MagicLink
import com.runcriticon.identidad.domain.magiclink.MagicLinkPurpose
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.SessionRevoker
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID

class ConsumePasswordResetTest :
    FunSpec({
        val club = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val userId = UserId.new()
        val rawToken = "raw-xyz"
        val tokenHash = TokenHash("hashed")

        val userRepository = mockk<UserRepository>(relaxed = true)
        val magicLinkRepository = mockk<MagicLinkRepository>(relaxed = true)
        val tokenHasher = mockk<TokenHasher>()
        val passwordHasher = mockk<PasswordHasher>()
        val passwordPolicy = mockk<PasswordPolicy>()
        val passwordHistory = mockk<PasswordHistory>(relaxed = true)
        val sessionRevoker = mockk<SessionRevoker>(relaxed = true)
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val useCase =
            ConsumePasswordReset(
                userRepository,
                magicLinkRepository,
                tokenHasher,
                passwordHasher,
                passwordPolicy,
                passwordHistory,
                sessionRevoker,
                auditTrail,
            )

        val newPassword = "clave-clave-clave"

        fun user(
            status: UserStatus = UserStatus.ACTIVO,
            passwordHash: String? = "hash",
        ) = User(
            id = userId,
            clubId = club,
            email = Email.of("ana@club.local"),
            name = "Ana",
            role = Role.ENTRENADOR,
            passwordHash = passwordHash,
            status = status,
        )

        fun resetLink(purpose: MagicLinkPurpose = MagicLinkPurpose.RESETEO) =
            MagicLink.issue(userId, club, tokenHash, purpose, java.time.Instant.now())

        beforeTest {
            clearMocks(
                userRepository,
                magicLinkRepository,
                tokenHasher,
                passwordHasher,
                passwordPolicy,
                passwordHistory,
                sessionRevoker,
                auditTrail,
            )
            every { tokenHasher.hash(RawToken(rawToken)) } returns tokenHash
            every { magicLinkRepository.findByTokenHash(tokenHash) } returns resetLink()
            every { userRepository.findByIdUnscoped(club, userId) } returns user()
            every { passwordPolicy.validate(any(), any()) } returns Unit.right()
            every { passwordHasher.encode(any()) } returns "new-hash"
        }

        test("token de reseteo válido cambia contraseña, registra histórico, revoca sesiones y audita") {
            val auditSlot = slot<AuditEntry>()
            every { auditTrail.record(capture(auditSlot)) } returns Unit
            val mlSlot = slot<MagicLink>()
            every { magicLinkRepository.save(capture(mlSlot)) } returns Unit

            val principal = useCase.execute(rawToken, newPassword).shouldBeRight()

            principal.userId shouldBe userId.value
            principal.role shouldBe Role.ENTRENADOR
            mlSlot.captured.consumedAt.shouldNotBeNull()
            verify { userRepository.save(any()) }
            verify { passwordHistory.record(userId, club, "new-hash", any()) }
            verify { sessionRevoker.revokeAll(userId.value) }
            auditSlot.captured.type shouldBe AuditEventType.PASSWORD_CAMBIADA
        }

        test("token inexistente devuelve InvalidInput y no cambia nada") {
            every { magicLinkRepository.findByTokenHash(tokenHash) } returns null

            useCase.execute(rawToken, newPassword).shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>()

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
        }

        test("token de propósito LOGIN se rechaza en el reseteo (aislamiento de propósito)") {
            every { magicLinkRepository.findByTokenHash(tokenHash) } returns resetLink(MagicLinkPurpose.LOGIN)

            useCase.execute(rawToken, newPassword).shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>()

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
        }

        test("magic link caducado devuelve InvalidInput y no revoca sesiones") {
            every { magicLinkRepository.findByTokenHash(tokenHash) } returns
                resetLink().copy(
                    expiresAt =
                        java.time.Instant
                            .now()
                            .minusSeconds(1),
                )

            useCase.execute(rawToken, newPassword).shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>()

            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
        }

        test("magic link ya usado devuelve Conflict") {
            every { magicLinkRepository.findByTokenHash(tokenHash) } returns
                resetLink().copy(consumedAt = java.time.Instant.now())

            useCase.execute(rawToken, newPassword).shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()
        }

        test("cuenta no activa devuelve AccountNotActive") {
            every { userRepository.findByIdUnscoped(club, userId) } returns user(UserStatus.DESACTIVADO)

            useCase.execute(rawToken, newPassword).shouldBeLeft(IdentidadError.AccountNotActive)

            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
        }

        test("contraseña que incumple la política D6 se rechaza y no cambia nada") {
            every { passwordPolicy.validate(any(), any()) } returns
                IdentidadError.InvalidInput("password", "too_short").left()

            useCase.execute(rawToken, newPassword).shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>()

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { sessionRevoker.revokeAll(any()) }
        }

        test("usuario solo-magic-link (sin contraseña previa) fija su primera contraseña") {
            every { userRepository.findByIdUnscoped(club, userId) } returns user(passwordHash = null)

            val principal = useCase.execute(rawToken, newPassword).shouldBeRight()

            principal.userId shouldBe userId.value
            verify { userRepository.save(any()) }
            verify { sessionRevoker.revokeAll(userId.value) }
        }
    })
