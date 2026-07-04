package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.InvitationRepository
import com.runcriticon.identidad.application.ports.TokenGenerator
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
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
import com.runcriticon.shared.autorizacion.model.Principal
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

class InviteCoachTest :
    FunSpec({
        val club = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val admin = Principal(userId = UUID.randomUUID(), clubId = club, role = Role.ADMIN)

        val userRepository = mockk<UserRepository>(relaxed = true)
        val invitationRepository = mockk<InvitationRepository>(relaxed = true)
        val tokenGenerator = mockk<TokenGenerator>()
        val tokenHasher = mockk<TokenHasher>()
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val metrics = mockk<RateLimitMetrics>(relaxed = true)
        val useCase =
            InviteCoach(
                userRepository,
                invitationRepository,
                tokenGenerator,
                tokenHasher,
                auditTrail,
                eventPublisher,
                rateLimiter,
                metrics,
            )

        beforeTest {
            clearMocks(
                userRepository,
                invitationRepository,
                tokenGenerator,
                tokenHasher,
                auditTrail,
                eventPublisher,
                rateLimiter,
                metrics,
            )
            every { tokenGenerator.generate() } returns RawToken("raw-token")
            every { tokenHasher.hash(any()) } returns TokenHash("hashed")
            every { userRepository.findByEmail(any(), any()) } returns null
            every { rateLimiter.tryConsume(any(), any()) } returns RateLimitDecision.Allowed
        }

        test("admin invita: crea entrenador INVITADO, emite invitación, publica email y audita") {
            val userSlot = slot<User>()
            val eventSlot = slot<Any>()
            val auditSlot = slot<AuditEntry>()
            every { userRepository.save(capture(userSlot)) } returns Unit
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            val createdId = useCase.execute(admin, "Carlos", "Carlos@Club.local").shouldBeRight()

            val saved = userSlot.captured
            createdId shouldBe saved.id
            saved.clubId shouldBe club
            saved.role shouldBe Role.ENTRENADOR
            saved.status shouldBe UserStatus.INVITADO
            saved.passwordHash shouldBe null
            saved.email shouldBe Email.of("carlos@club.local")

            verify { invitationRepository.save(any()) }

            val email = eventSlot.captured.shouldBeInstanceOf<InvitationEmailRequested>()
            email.to shouldBe Email.of("carlos@club.local")
            email.recipientName shouldBe "Carlos"
            email.rawToken shouldBe RawToken("raw-token")

            auditSlot.captured.type shouldBe AuditEventType.INVITACION_EMITIDA
            auditSlot.captured.actorId shouldBe admin.userId
            auditSlot.captured.subjectId shouldBe saved.id.value
        }

        test("email duplicado en el club devuelve Conflict y no crea nada") {
            every { userRepository.findByEmail(club, Email.of("dup@club.local")) } returns
                User(
                    id = UserId.new(),
                    clubId = club,
                    email = Email.of("dup@club.local"),
                    name = "Ya existe",
                    role = Role.ENTRENADOR,
                    passwordHash = null,
                    status = UserStatus.INVITADO,
                )

            useCase
                .execute(admin, "Carlos", "dup@club.local")
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { invitationRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }

        test("actor sin rol ADMIN devuelve Forbidden y no produce efectos") {
            val coach = Principal(userId = UUID.randomUUID(), clubId = club, role = Role.ENTRENADOR)

            useCase
                .execute(coach, "Carlos", "carlos@club.local")
                .shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }

        test("nombre en blanco devuelve InvalidInput sobre el campo name") {
            val error =
                useCase
                    .execute(admin, "   ", "carlos@club.local")
                    .shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.InvalidInput>()
            error.field shouldBe "name"

            verify(exactly = 0) { userRepository.save(any()) }
        }

        test("límite por actor alcanzado devuelve RateLimited y no crea nada") {
            every { rateLimiter.tryConsume(any(), any()) } returns RateLimitDecision.Limited(Duration.ofSeconds(30))
            val auditSlot = slot<AuditEntry>()
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            val error =
                useCase
                    .execute(admin, "Carlos", "carlos@club.local")
                    .shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.RateLimited>()
            error.retryAfterSeconds shouldBe 30L

            verify { metrics.blocked("invitacion", "actor") }
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            auditSlot.captured.type shouldBe AuditEventType.INVITACION_RATE_LIMITED
        }
    })
