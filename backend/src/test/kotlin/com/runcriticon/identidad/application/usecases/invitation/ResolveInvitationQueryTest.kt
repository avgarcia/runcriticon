package com.runcriticon.identidad.application.usecases.invitation

import com.runcriticon.identidad.application.ports.outbound.persistence.ClubRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.ports.outbound.security.TokenHasher
import com.runcriticon.identidad.application.ratelimit.RateLimitDecision
import com.runcriticon.identidad.application.ratelimit.RateLimitMetrics
import com.runcriticon.identidad.application.ratelimit.RateLimitScope
import com.runcriticon.identidad.application.ratelimit.RateLimiter
import com.runcriticon.identidad.domain.club.Club
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
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
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ResolveInvitationQueryTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val invitedId = UserId.new()
        val inviterId = UserId.new()
        val rawToken = "raw-token-xyz"
        val tokenHash = TokenHash("hashed")
        val clientIp = "203.0.113.10"

        val invitationRepository = mockk<InvitationRepository>()
        val userRepository = mockk<UserRepository>()
        val clubRepository = mockk<ClubRepository>()
        val tokenHasher = mockk<TokenHasher>()
        val rateLimiter = mockk<RateLimiter>()
        val rateLimitMetrics = mockk<RateLimitMetrics>(relaxed = true)
        val useCase =
            ResolveInvitationQuery(
                invitationRepository,
                userRepository,
                clubRepository,
                tokenHasher,
                rateLimiter,
                rateLimitMetrics,
            )

        val invited =
            User(
                id = invitedId,
                clubId = club,
                email = Email.of("andrea@club.local"),
                name = "Andrea",
                role = Role.ALUMNO,
                passwordHash = null,
                status = UserStatus.INVITADO,
            )
        val inviter =
            User(
                id = inviterId,
                clubId = club,
                email = Email.of("ana@club.local"),
                name = "Ana Pinares",
                role = Role.ENTRENADOR,
                passwordHash = "hash",
                status = UserStatus.ACTIVO,
            )
        val clubDomain = Club(id = club, name = "Club Atletismo Pinares", slug = null)
        val openInvitation =
            Invitation.issue(invitedId, club, tokenHash, invitedBy = inviterId, now = Instant.now())

        beforeTest {
            clearMocks(invitationRepository, userRepository, clubRepository, tokenHasher, rateLimiter)
            every { rateLimiter.tryConsume(RateLimitScope.INVITATION_LOOKUP_IP, clientIp) } returns
                RateLimitDecision.Allowed
            every { tokenHasher.hash(RawToken(rawToken)) } returns tokenHash
            every { invitationRepository.findByTokenHash(tokenHash) } returns openInvitation
            every { userRepository.findByIdUnscoped(club, invitedId) } returns invited
            every { userRepository.findByIdUnscoped(club, inviterId) } returns inviter
            every { clubRepository.findByIdUnscoped(club) } returns clubDomain
        }

        test("devuelve nombre, club, rol e invitador cuando la invitación es válida") {
            val details = useCase.execute(rawToken, clientIp).shouldBeRight()

            details.name shouldBe "Andrea"
            details.club shouldBe "Club Atletismo Pinares"
            details.role shouldBe Role.ALUMNO
            details.invitedBy shouldBe "Ana Pinares"
        }

        test("invitación sin invitador registrado (previa a LAL-64) omite invitedBy sin fallar") {
            every { invitationRepository.findByTokenHash(tokenHash) } returns
                openInvitation.copy(invitedBy = null)

            val details = useCase.execute(rawToken, clientIp).shouldBeRight()

            details.invitedBy shouldBe null
            verify(exactly = 0) { userRepository.findByIdUnscoped(club, inviterId) }
        }

        test("token sin invitación devuelve InvalidInput(token)") {
            every { invitationRepository.findByTokenHash(tokenHash) } returns null

            val error =
                useCase
                    .execute(
                        rawToken,
                        clientIp,
                    ).shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.InvalidInput>()
            error.field shouldBe "token"
        }

        test("invitación caducada devuelve InvalidInput sin mutar nada") {
            val expired =
                openInvitation.copy(
                    issuedAt = Instant.now().minus(Duration.ofDays(8)),
                    expiresAt = Instant.now().minus(Duration.ofDays(1)),
                )
            every { invitationRepository.findByTokenHash(tokenHash) } returns expired

            useCase.execute(rawToken, clientIp).shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>()
            verify(exactly = 0) { invitationRepository.save(any()) }
        }

        test("invitación ya consumida devuelve Conflict") {
            every { invitationRepository.findByTokenHash(tokenHash) } returns
                openInvitation.copy(consumedAt = Instant.now())

            useCase.execute(rawToken, clientIp).shouldBeLeft().shouldBeInstanceOf<IdentidadError.Conflict>()
        }

        test("límite de IP agotado devuelve RateLimited antes de tocar la invitación") {
            every { rateLimiter.tryConsume(RateLimitScope.INVITATION_LOOKUP_IP, clientIp) } returns
                RateLimitDecision.Limited(Duration.ofSeconds(30))

            val error =
                useCase
                    .execute(
                        rawToken,
                        clientIp,
                    ).shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.RateLimited>()
            error.retryAfterSeconds shouldBe 30L
            verify(exactly = 0) { invitationRepository.findByTokenHash(any()) }
        }
    })
