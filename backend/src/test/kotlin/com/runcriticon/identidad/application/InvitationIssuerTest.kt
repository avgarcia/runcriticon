package com.runcriticon.identidad.application
import com.runcriticon.identidad.application.ports.inbound.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.ports.outbound.security.TokenGenerator
import com.runcriticon.identidad.application.ports.outbound.security.TokenHasher
import com.runcriticon.identidad.application.ratelimit.RateLimitDecision
import com.runcriticon.identidad.application.ratelimit.RateLimitMetrics
import com.runcriticon.identidad.application.ratelimit.RateLimiter
import com.runcriticon.identidad.application.usecases.invitation.InviteCoachCommand
import com.runcriticon.identidad.application.usecases.invitation.InviteStudentCommand
import com.runcriticon.identidad.application.usecases.invitation.ResendInvitationCommand
import com.runcriticon.identidad.application.usecases.invitation.ResendStudentInvitationCommand
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
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
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Orquestación compartida por [InviteCoachCommand], [InviteStudentCommand], [ResendInvitationCommand] y
 * [ResendStudentInvitationCommand] (LAL-62): toda la lógica de rate-limit, validación, token, email y
 * auditoría se prueba una sola vez aquí; los tests de cada cascarón mockean [InvitationIssuer] y
 * solo cubren lo que les es propio (matriz de autorización, delegación, eventos de recurso).
 */
class InvitationIssuerTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

        val userRepository = mockk<UserRepository>(relaxed = true)
        val invitationRepository = mockk<InvitationRepository>(relaxed = true)
        val tokenGenerator = mockk<TokenGenerator>()
        val tokenHasher = mockk<TokenHasher>()
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>()
        val metrics = mockk<RateLimitMetrics>(relaxed = true)
        val issuer =
            InvitationIssuer(
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

        // --- issue() ---

        test("issue: crea el usuario INVITADO con el rol dado, emite invitación, publica email y audita") {
            val userSlot = slot<User>()
            val eventSlot = slot<Any>()
            val auditSlot = slot<AuditEntry>()
            every { userRepository.save(capture(userSlot)) } returns Unit
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            val created = issuer.issue(admin, "Carlos", "Carlos@Club.local", Role.ENTRENADOR).shouldBeRight()

            val saved = userSlot.captured
            created.user shouldBe saved
            created.actorId shouldBe admin.userId
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

        test("issue: nombre en blanco devuelve InvalidInput sobre el campo name") {
            val error =
                issuer
                    .issue(admin, "   ", "carlos@club.local", Role.ENTRENADOR)
                    .shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.InvalidInput>()
            error.field shouldBe "name"

            verify(exactly = 0) { userRepository.save(any()) }
        }

        test("issue: email sin arroba devuelve InvalidInput sobre el campo email") {
            val error =
                issuer
                    .issue(admin, "Carlos", "no-es-un-email", Role.ENTRENADOR)
                    .shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.InvalidInput>()
            error.field shouldBe "email"

            verify(exactly = 0) { userRepository.save(any()) }
        }

        test("issue: email duplicado en el club devuelve Conflict y no crea nada") {
            every { userRepository.findByEmail(club, Email.of("dup@club.local")) } returns
                User.newInvited(club, Email.of("dup@club.local"), "Ya existe", Role.ENTRENADOR)

            issuer
                .issue(admin, "Carlos", "dup@club.local", Role.ENTRENADOR)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { invitationRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            verify(exactly = 0) { auditTrail.record(any()) }
        }

        test("issue: límite por actor alcanzado devuelve RateLimited y no crea nada") {
            every { rateLimiter.tryConsume(any(), any()) } returns RateLimitDecision.Limited(Duration.ofSeconds(30))
            val auditSlot = slot<AuditEntry>()
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            val error =
                issuer
                    .issue(admin, "Carlos", "carlos@club.local", Role.ENTRENADOR)
                    .shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.RateLimited>()
            error.retryAfterSeconds shouldBe 30L

            verify { metrics.blocked("invitacion", "actor") }
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
            auditSlot.captured.type shouldBe AuditEventType.INVITACION_RATE_LIMITED
        }

        // --- reissueFor() ---

        val coachId = UserId.new()
        val studentId = UserId.new()

        val invitadoCoach =
            User(
                id = coachId,
                clubId = club,
                email = Email.of("coach@club.local"),
                name = "Carlos",
                role = Role.ENTRENADOR,
                passwordHash = null,
                status = UserStatus.INVITADO,
            )

        val invitadoStudent =
            User(
                id = studentId,
                clubId = club,
                email = Email.of("marta@club.local"),
                name = "Marta",
                role = Role.ALUMNO,
                passwordHash = null,
                status = UserStatus.INVITADO,
            )

        val coachInvitation =
            Invitation.issue(userId = coachId, clubId = club, tokenHash = TokenHash("old-hash"), now = Instant.now())

        beforeTest {
            every { userRepository.findById(club, coachId) } returns invitadoCoach
            every { userRepository.findById(club, studentId) } returns invitadoStudent
            every { invitationRepository.findLatestByUserId(coachId) } returns coachInvitation
        }

        test("reissueFor: invalida la invitación anterior, guarda la nueva, publica email y audita") {
            val savedInvitations = mutableListOf<Invitation>()
            val eventSlot = slot<Any>()
            val auditSlot = slot<AuditEntry>()
            every { invitationRepository.save(capture(savedInvitations)) } returns Unit
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            issuer.reissueFor(admin, coachId, Role.ENTRENADOR).shouldBeRight()

            savedInvitations.size shouldBe 2
            savedInvitations.first().consumedAt.shouldNotBeNull()
            savedInvitations.last().consumedAt shouldBe null
            savedInvitations.last().tokenHash shouldBe TokenHash("hashed")

            val email = eventSlot.captured.shouldBeInstanceOf<InvitationEmailRequested>()
            email.to shouldBe invitadoCoach.email
            email.rawToken shouldBe RawToken("raw-token")

            auditSlot.captured.type shouldBe AuditEventType.INVITACION_EMITIDA
            auditSlot.captured.actorId shouldBe admin.userId
            auditSlot.captured.subjectId shouldBe invitadoCoach.id.value
        }

        test("reissueFor: un alumno reenviado con expectedRole ENTRENADOR devuelve NotFound (regresión LAL-62)") {
            issuer
                .reissueFor(admin, studentId, Role.ENTRENADOR)
                .shouldBeLeft(IdentidadError.NotFound)

            verify(exactly = 0) { invitationRepository.save(any()) }
        }

        test("reissueFor: un entrenador reenviado con expectedRole ALUMNO devuelve NotFound") {
            issuer
                .reissueFor(admin, coachId, Role.ALUMNO)
                .shouldBeLeft(IdentidadError.NotFound)

            verify(exactly = 0) { invitationRepository.save(any()) }
        }

        test("reissueFor: usuario no encontrado devuelve NotFound") {
            every { userRepository.findById(club, coachId) } returns null

            issuer.reissueFor(admin, coachId, Role.ENTRENADOR).shouldBeLeft(IdentidadError.NotFound)

            verify(exactly = 0) { invitationRepository.save(any()) }
        }

        test("reissueFor: usuario en estado ACTIVO devuelve Conflict y no guarda nada") {
            every { userRepository.findById(club, coachId) } returns invitadoCoach.copy(status = UserStatus.ACTIVO)

            issuer
                .reissueFor(admin, coachId, Role.ENTRENADOR)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { invitationRepository.save(any()) }
        }

        test("reissueFor: sin invitación previa devuelve Conflict") {
            every { invitationRepository.findLatestByUserId(coachId) } returns null

            issuer
                .reissueFor(admin, coachId, Role.ENTRENADOR)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { invitationRepository.save(any()) }
        }
    })
