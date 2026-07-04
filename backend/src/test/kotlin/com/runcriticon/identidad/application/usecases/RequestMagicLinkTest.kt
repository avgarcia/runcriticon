package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.EmailHasher
import com.runcriticon.identidad.application.ports.MagicLinkEmailRequested
import com.runcriticon.identidad.application.ports.MagicLinkRepository
import com.runcriticon.identidad.application.ports.TokenGenerator
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.application.ratelimit.ProgressiveThrottle
import com.runcriticon.identidad.application.ratelimit.RateLimitDecision
import com.runcriticon.identidad.application.ratelimit.RateLimitMetrics
import com.runcriticon.identidad.application.ratelimit.RateLimiter
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Role
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
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.util.UUID

class RequestMagicLinkTest :
    FunSpec({
        val club = UUID.fromString("00000000-0000-0000-0000-000000000001")

        val userRepository = mockk<UserRepository>()
        val magicLinkRepository = mockk<MagicLinkRepository>(relaxed = true)
        val tokenGenerator = mockk<TokenGenerator>()
        val tokenHasher = mockk<TokenHasher>()
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val throttle = mockk<ProgressiveThrottle>(relaxed = true)
        val metrics = mockk<RateLimitMetrics>(relaxed = true)
        val emailHasher = mockk<EmailHasher>(relaxed = true)
        val useCase =
            RequestMagicLink(
                userRepository,
                magicLinkRepository,
                tokenGenerator,
                tokenHasher,
                auditTrail,
                eventPublisher,
                rateLimiter,
                throttle,
                metrics,
                emailHasher,
            )

        val ip = "203.0.113.7"

        fun activeUser() =
            User(
                id = UserId.new(),
                clubId = club,
                email = Email.of("ana@club.local"),
                name = "Ana",
                role = Role.ALUMNO,
                passwordHash = "hash",
                status = UserStatus.ACTIVO,
            )

        beforeTest {
            clearMocks(
                userRepository,
                magicLinkRepository,
                tokenGenerator,
                tokenHasher,
                auditTrail,
                eventPublisher,
                rateLimiter,
                throttle,
                metrics,
                emailHasher,
            )
            every { tokenGenerator.generate() } returns RawToken("raw-xyz")
            every { tokenHasher.hash(any()) } returns TokenHash("hashed")
            // Por defecto hay cupo: sin límite ni cooldown.
            every { rateLimiter.tryConsume(any(), any()) } returns RateLimitDecision.Allowed
            every { throttle.check(any(), any()) } returns null
        }

        test("email de cuenta activa emite magic link, evento y auditoría") {
            every { userRepository.findByEmail(club, any()) } returns activeUser()
            val events = mutableListOf<Any>()
            every { eventPublisher.publishEvent(capture(events)) } returns Unit
            val auditSlot = slot<AuditEntry>()
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            useCase.execute(club, "ana@club.local", ip).shouldBeRight()

            verify { magicLinkRepository.save(any()) }
            val published = events.filterIsInstance<MagicLinkEmailRequested>().single()
            published.to.value shouldBe "ana@club.local"
            published.rawToken.value shouldBe "raw-xyz"
            auditSlot.captured.type shouldBe AuditEventType.MAGIC_LINK_EMITIDO
        }

        test("email inexistente devuelve Right neutro sin emitir nada") {
            every { userRepository.findByEmail(club, any()) } returns null

            useCase.execute(club, "nadie@club.local", ip).shouldBeRight()

            verify(exactly = 0) { magicLinkRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }

        test("cuenta no activa (INVITADO) devuelve Right neutro sin emitir nada") {
            every { userRepository.findByEmail(club, any()) } returns activeUser().copy(status = UserStatus.INVITADO)

            useCase.execute(club, "ana@club.local", ip).shouldBeRight()

            verify(exactly = 0) { magicLinkRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        test("email malformado devuelve InvalidInput y no consulta el repositorio") {
            useCase
                .execute(club, "sin-arroba", ip)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.InvalidInput>()

            verify(exactly = 0) { userRepository.findByEmail(any(), any()) }
        }

        test("límite alcanzado: 202 neutro sin emitir y asiento MAGIC_LINK_RATE_LIMITED con email_hash e ip") {
            every { rateLimiter.tryConsume(any(), any()) } returns RateLimitDecision.Limited(Duration.ofMinutes(1))
            every { emailHasher.hash("ana@club.local") } returns "hash-ana"
            val auditSlot = slot<AuditEntry>()
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            useCase.execute(club, "ana@club.local", ip).shouldBeRight()

            verify(exactly = 0) { userRepository.findByEmail(any(), any()) }
            verify(exactly = 0) { magicLinkRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            verify { metrics.blocked("magic_link", any()) }
            auditSlot.captured.type shouldBe AuditEventType.MAGIC_LINK_RATE_LIMITED
            auditSlot.captured.metadata?.get("email_hash") shouldBe "hash-ana"
            auditSlot.captured.metadata?.get("ip") shouldBe ip
        }
    })
