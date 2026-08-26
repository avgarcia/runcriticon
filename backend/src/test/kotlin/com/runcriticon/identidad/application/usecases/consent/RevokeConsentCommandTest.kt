package com.runcriticon.identidad.application.usecases.consent

import com.runcriticon.identidad.api.events.ConsentimientoRevocado
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.consent.ConsentText
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.MutableClock
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class RevokeConsentCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val alumno = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)

        val consentRepository = mockk<ConsentRepository>(relaxed = true)
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val clock = MutableClock(Instant.parse("2026-08-25T12:00:00Z"))
        val command = RevokeConsentCommand(consentRepository, auditTrail, eventPublisher, clock)

        val activeConsent =
            Consent.grant(
                UserId.of(alumno.userId),
                club,
                ConsentText.CURRENT_VERSION,
                "203.0.113.10",
                "test-agent",
                Instant.parse("2026-08-20T10:00:00Z"),
            )

        beforeTest {
            clearMocks(consentRepository, auditTrail, eventPublisher)
        }

        test("revoca la fila vigente y publica ConsentimientoRevocado") {
            every { consentRepository.findLatestByUserId(club, UserId.of(alumno.userId)) } returns activeConsent
            val consentSlot = slot<Consent>()
            every { consentRepository.save(capture(consentSlot)) } returns Unit
            val events = mutableListOf<Any>()
            every { eventPublisher.publishEvent(capture(events)) } returns Unit

            val result = command.execute(alumno).shouldBeRight()

            result.isActive() shouldBe false
            consentSlot.captured.id shouldBe activeConsent.id
            events.filterIsInstance<ConsentimientoRevocado>().single().aggregateId shouldBe alumno.userId
        }

        test("sin ninguna concesion, es Conflict y no persiste nada") {
            every { consentRepository.findLatestByUserId(club, UserId.of(alumno.userId)) } returns null

            command.execute(alumno).shouldBeLeft()

            verify(exactly = 0) { consentRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        test("una fila ya revocada no se puede revocar otra vez") {
            val alreadyRevoked = activeConsent.revoke(Instant.parse("2026-08-21T10:00:00Z"))
            every { consentRepository.findLatestByUserId(club, UserId.of(alumno.userId)) } returns alreadyRevoked

            command
                .execute(
                    alumno,
                ).shouldBeLeft()
                .shouldBe(IdentidadError.Conflict("el consentimiento ya está revocado"))

            verify(exactly = 0) { consentRepository.save(any()) }
        }
    })
