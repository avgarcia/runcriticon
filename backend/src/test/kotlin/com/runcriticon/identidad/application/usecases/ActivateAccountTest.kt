package com.runcriticon.identidad.application.usecases

import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.api.events.AlumnoActivado
import com.runcriticon.identidad.api.events.EntrenadorActivado
import com.runcriticon.identidad.application.PasswordPolicy
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.BusinessMetrics
import com.runcriticon.identidad.application.ports.InvitationRepository
import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.PasswordHistory
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
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

        val userRepository = mockk<UserRepository>(relaxed = true)
        val invitationRepository = mockk<InvitationRepository>(relaxed = true)
        val tokenHasher = mockk<TokenHasher>()
        val passwordHasher = mockk<PasswordHasher>()
        val passwordPolicy = mockk<PasswordPolicy>()
        val passwordHistory = mockk<PasswordHistory>(relaxed = true)
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val businessMetrics = mockk<BusinessMetrics>(relaxed = true)
        val useCase =
            ActivateAccount(
                userRepository,
                invitationRepository,
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

        test("activa un alumno: ACTIVO, histórico, auditoría, AlumnoActivado y Principal") {
            val userSlot = slot<User>()
            val events = mutableListOf<Any>()
            val auditSlot = slot<AuditEntry>()
            every { userRepository.save(capture(userSlot)) } returns Unit
            every { eventPublisher.publishEvent(capture(events)) } returns Unit
            every { auditTrail.record(capture(auditSlot)) } returns Unit

            val principal = useCase.execute(rawToken, validPassword).shouldBeRight()

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

            auditSlot.captured.type shouldBe AuditEventType.INVITACION_ACTIVADA
            auditSlot.captured.subjectId shouldBe userId.value

            verify { businessMetrics.accountActivated(Role.ALUMNO) }
        }

        test("activa un entrenador: publica EntrenadorActivado") {
            every { userRepository.findByIdUnscoped(club, userId) } returns invitedAlumno.copy(role = Role.ENTRENADOR)
            val events = mutableListOf<Any>()
            every { eventPublisher.publishEvent(capture(events)) } returns Unit

            useCase.execute(rawToken, validPassword).shouldBeRight()

            events.filterIsInstance<EntrenadorActivado>().single()
            events.any { it is AlumnoActivado } shouldBe false
            verify { businessMetrics.accountActivated(Role.ENTRENADOR) }
        }

        test("token sin invitación devuelve InvalidInput(token) y no activa") {
            every { invitationRepository.findByTokenHash(tokenHash) } returns null

            val error =
                useCase
                    .execute(rawToken, validPassword)
                    .shouldBeLeft()
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
                .execute(rawToken, validPassword)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.InvalidInput>()

            verify(exactly = 0) { userRepository.save(any()) }
        }

        test("invitación ya consumida devuelve Conflict") {
            every { invitationRepository.findByTokenHash(tokenHash) } returns
                openInvitation.copy(consumedAt = Instant.now())

            useCase
                .execute(rawToken, validPassword)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.save(any()) }
        }

        test("cuenta ya activa devuelve Conflict y no vuelve a activar") {
            every { userRepository.findByIdUnscoped(club, userId) } returns
                invitedAlumno.copy(status = UserStatus.ACTIVO)

            useCase
                .execute(rawToken, validPassword)
                .shouldBeLeft()
                .shouldBeInstanceOf<IdentidadError.Conflict>()

            verify(exactly = 0) { userRepository.save(any()) }
        }

        test("contraseña que incumple la política devuelve InvalidInput(password) y no activa") {
            every { passwordPolicy.validate(any(), any()) } returns
                IdentidadError.InvalidInput("password", "too_short").left()

            val error =
                useCase.execute(rawToken, "corta").shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>()
            error.field shouldBe "password"

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }
    })
