package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.MagicLinkRepository
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
import java.time.Instant
import java.util.UUID

class ConsumeMagicLinkTest :
    FunSpec({
        val club = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val userId = UserId.new()
        val rawToken = "raw-xyz"
        val tokenHash = TokenHash("hashed")
        val now = Instant.now()

        val userRepository = mockk<UserRepository>(relaxed = true)
        val magicLinkRepository = mockk<MagicLinkRepository>(relaxed = true)
        val tokenHasher = mockk<TokenHasher>()
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val useCase = ConsumeMagicLink(userRepository, magicLinkRepository, tokenHasher, auditTrail)

        val openLink = MagicLink.issue(userId, club, tokenHash, MagicLinkPurpose.LOGIN, now)

        fun user(status: UserStatus = UserStatus.ACTIVO) =
            User(
                id = userId,
                clubId = club,
                email = Email.of("ana@club.local"),
                name = "Ana",
                role = Role.ENTRENADOR,
                passwordHash = "hash",
                status = status,
            )

        beforeTest {
            clearMocks(userRepository, magicLinkRepository, tokenHasher, auditTrail)
            every { tokenHasher.hash(RawToken(rawToken)) } returns tokenHash
            every { magicLinkRepository.findByTokenHash(tokenHash) } returns openLink
            every { userRepository.findByIdUnscoped(club, userId) } returns user()
        }

        test("token válido de cuenta activa devuelve Principal, consume y audita") {
            val auditSlot = slot<AuditEntry>()
            every { auditTrail.record(capture(auditSlot)) } returns Unit
            val mlSlot = slot<MagicLink>()
            every { magicLinkRepository.save(capture(mlSlot)) } returns Unit

            val principal = useCase.execute(rawToken).shouldBeRight()

            principal.userId shouldBe userId.value
            principal.role shouldBe Role.ENTRENADOR
            mlSlot.captured.consumedAt.shouldNotBeNull()
            auditSlot.captured.type shouldBe AuditEventType.MAGIC_LINK_USADO
        }

        test("token inexistente devuelve InvalidInput y no consume") {
            every { magicLinkRepository.findByTokenHash(tokenHash) } returns null

            useCase.execute(rawToken).shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>()

            verify(exactly = 0) { magicLinkRepository.save(any()) }
        }

        test("cuenta no activa devuelve AccountNotActive") {
            every { userRepository.findByIdUnscoped(club, userId) } returns user(UserStatus.DESACTIVADO)

            useCase.execute(rawToken).shouldBeLeft(IdentidadError.AccountNotActive)

            verify(exactly = 0) { magicLinkRepository.save(any()) }
        }

        test("magic link caducado devuelve InvalidInput") {
            every { magicLinkRepository.findByTokenHash(tokenHash) } returns
                openLink.copy(expiresAt = now.minusSeconds(1))

            useCase.execute(rawToken).shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>()
        }

        test("magic link ya usado devuelve Conflict") {
            every { magicLinkRepository.findByTokenHash(tokenHash) } returns openLink.copy(consumedAt = now)

            useCase.execute(rawToken).shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()
        }

        test("token de propósito RESETEO no se consume como login (aislamiento de propósito)") {
            every { magicLinkRepository.findByTokenHash(tokenHash) } returns
                openLink.copy(proposito = MagicLinkPurpose.RESETEO)

            useCase.execute(rawToken).shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>()

            verify(exactly = 0) { magicLinkRepository.save(any()) }
        }
    })
