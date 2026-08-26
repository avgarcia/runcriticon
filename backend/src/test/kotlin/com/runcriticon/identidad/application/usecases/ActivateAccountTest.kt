package com.runcriticon.identidad.application.usecases
import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.api.events.AlumnoActivado
import com.runcriticon.identidad.api.events.ConsentimientoConcedido
import com.runcriticon.identidad.api.events.EntrenadorActivado
import com.runcriticon.identidad.application.PasswordPolicy
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.observability.BusinessMetrics
import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.PasswordHistory
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.ports.outbound.security.PasswordHasher
import com.runcriticon.identidad.application.ports.outbound.security.TokenHasher
import com.runcriticon.identidad.application.usecases.account.ActivateAccountCommand
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.consent.ConsentText
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
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ActivateAccountTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val userId = UserId.new()
        val rawToken = "raw-token-xyz"
        val tokenHash = TokenHash("hashed")
        val validPassword = "clave-clave-clave"
        val clientIp = "203.0.113.10"
        val userAgent = "jest-agent/1.0"

        val userRepository = mockk<UserRepository>(relaxed = true)
        val invitationRepository = mockk<InvitationRepository>(relaxed = true)
        val consentRepository = mockk<ConsentRepository>(relaxed = true)
        val tokenHasher = mockk<TokenHasher>()
        val passwordHasher = mockk<PasswordHasher>()
        val passwordPolicy = mockk<PasswordPolicy>()
        val passwordHistory = mockk<PasswordHistory>(relaxed = true)
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val businessMetrics = mockk<BusinessMetrics>(relaxed = true)
        val useCase =
            ActivateAccountCommand(
                userRepository,
                invitationRepository,
                consentRepository,
                tokenHasher,
                passwordHasher,
                passwordPolicy,
                passwordHistory,
                auditTrail,
                eventPublisher,
                businessMetrics,
            )

        val invitedAlumno =
            User(
                id = userId,
                clubId = club,
                email = Email.of("marta@club.local"),
                name = "Marta",
                role = Role.ALUMNO,
                passwordHash = null,
                status = UserStatus.INVITADO,
            )
        val openInvitation = Invitation.issue(userId, club, tokenHash, Instant.now())

        beforeTest {
            clearMocks(
                userRepository,
                invitationRepository,
                consentRepository,
                tokenHasher,
                passwordHasher,
                passwordPolicy,
                passwordHistory,
                auditTrail,
                eventPublisher,
                businessMetrics,
            )
            every { tokenHasher.hash(RawToken(rawToken)) } returns tokenHash
            every { invitationRepository.findByTokenHash(tokenHash) } returns openInvitation
            every { userRepository.findByIdUnscoped(club, userId) } returns invitedAlumno
            every { passwordPolicy.validate(any(), any()) } returns Unit.right()
            every { passwordHasher.encode(any()) } returns "encoded-hash"
        }

        test("activa un alumno: ACTIVO, histórico, auditoría, AlumnoActivado, consentimiento y Principal") {
            val userSlot = slot<User>()
            val events = mutableListOf<Any>()
            val auditEntries = mutableListOf<AuditEntry>()
            val consentSlot = slot<Consent>()
            every { userRepository.save(capture(userSlot)) } returns Unit
            every { eventPublisher.publishEvent(capture(events)) } returns Unit
            every { auditTrail.record(capture(auditEntries)) } returns Unit
            every { consentRepository.save(capture(consentSlot)) } returns Unit

            val principal =
                useCase
                    .execute(
                        rawToken,
                        validPassword,
                        consentGranted = true,
                        ConsentText.CURRENT_VERSION,
                        clientIp,
                        userAgent,
                    ).shouldBeRight()

            val saved = userSlot.captured
            saved.status shouldBe UserStatus.ACTIVO
            saved.passwordHash shouldBe "encoded-hash"
            principal.userId shouldBe userId.value
            principal.role shouldBe Role.ALUMNO

            verify { invitationRepository.save(any()) }
            verify { passwordHistory.record(userId, club, "encoded-hash", any()) }

            val published = events.filterIsInstance<AlumnoActivado>().single()
            published.aggregateId shouldBe userId.value
            published.clubId shouldBe club.value
            published.email shouldBe "marta@club.local"
            events.any { it is EntrenadorActivado } shouldBe false

            auditEntries.map { it.type } shouldBe
                listOf(AuditEventType.INVITACION_ACTIVADA, AuditEventType.CONSENTIMIENTO_CONCEDIDO)

            val consentEvent = events.filterIsInstance<ConsentimientoConcedido>().single()
            consentEvent.aggregateId shouldBe userId.value
            consentEvent.versionTexto shouldBe ConsentText.CURRENT_VERSION
            consentSlot.captured.ip shouldBe clientIp
            consentSlot.captured.userAgent shouldBe userAgent

            verify { businessMetrics.accountActivated(Role.ALUMNO) }
        }

        test("un alumno sin marcar la casilla no activa: ConsentRequired") {
            useCase
                .execute(
                    rawToken,
                    validPassword,
                    consentGranted = false,
                    ConsentText.CURRENT_VERSION,
                    clientIp,
                    userAgent,
                ).shouldBeLeft(IdentidadError.ConsentRequired)

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { consentRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        test("un alumno con una version de texto obsoleta no activa: ConsentTextOutdated") {
            useCase
                .execute(rawToken, validPassword, consentGranted = true, "v2020-01-01", clientIp, userAgent)
                .shouldBeLeft(IdentidadError.ConsentTextOutdated)

            verify(exactly = 0) { userRepository.save(any()) }
        }

        test("activa un entrenador: publica EntrenadorActivado, sin consentimiento") {
            every { userRepository.findByIdUnscoped(club, userId) } returns invitedAlumno.copy(role = Role.ENTRENADOR)
            val events = mutableListOf<Any>()
            every { eventPublisher.publishEvent(capture(events)) } returns Unit

            useCase
                .execute(rawToken, validPassword, consentGranted = false, consentVersion = null, clientIp, userAgent)
                .shouldBeRight()

            events.filterIsInstance<EntrenadorActivado>().single()
            events.any { it is AlumnoActivado } shouldBe false
            events.any { it is ConsentimientoConcedido } shouldBe false
            verify(exactly = 0) { consentRepository.save(any()) }
            verify { businessMetrics.accountActivated(Role.ENTRENADOR) }
        }

        test("token sin invitación devuelve InvalidInput(token) y no activa") {
            every { invitationRepository.findByTokenHash(tokenHash) } returns null

            val error =
                useCase
                    .execute(
                        rawToken,
                        validPassword,
                        consentGranted = true,
                        ConsentText.CURRENT_VERSION,
                        clientIp,
                        userAgent,
                    ).shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.InvalidInput>()
            error.field shouldBe "token"

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        test("invitación caducada devuelve InvalidInput y no activa") {
            val expired =
                openInvitation.copy(
                    issuedAt = Instant.now().minus(Duration.ofDays(8)),
                    expiresAt = Instant.now().minus(Duration.ofDays(1)),
                )
            every { invitationRepository.findByTokenHash(tokenHash) } returns expired

            useCase
                .execute(
                    rawToken,
                    validPassword,
                    consentGranted = true,
                    ConsentText.CURRENT_VERSION,
                    clientIp,
                    userAgent,
                ).shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.InvalidInput>()

            verify(exactly = 0) { userRepository.save(any()) }
        }

        test("invitación ya consumida devuelve Conflict") {
            every { invitationRepository.findByTokenHash(tokenHash) } returns
                openInvitation.copy(consumedAt = Instant.now())

            useCase
                .execute(
                    rawToken,
                    validPassword,
                    consentGranted = true,
                    ConsentText.CURRENT_VERSION,
                    clientIp,
                    userAgent,
                ).shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.save(any()) }
        }

        test("cuenta ya activa devuelve Conflict y no vuelve a activar") {
            every { userRepository.findByIdUnscoped(club, userId) } returns
                invitedAlumno.copy(status = UserStatus.ACTIVO)

            useCase
                .execute(
                    rawToken,
                    validPassword,
                    consentGranted = true,
                    ConsentText.CURRENT_VERSION,
                    clientIp,
                    userAgent,
                ).shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.save(any()) }
        }

        test("contraseña que incumple la política devuelve InvalidInput(password) y no activa") {
            every { passwordPolicy.validate(any(), any()) } returns
                IdentidadError.InvalidInput("password", "too_short").left()

            val error =
                useCase
                    .execute(rawToken, "corta", consentGranted = true, ConsentText.CURRENT_VERSION, clientIp, userAgent)
                    .shouldBeLeft()
                    .shouldBeInstanceOf<IdentidadError.InvalidInput>()
            error.field shouldBe "password"

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }
    })
