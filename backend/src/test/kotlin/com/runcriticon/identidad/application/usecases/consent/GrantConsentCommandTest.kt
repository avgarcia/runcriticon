package com.runcriticon.identidad.application.usecases.consent

import com.runcriticon.identidad.api.events.ConsentimientoConcedido
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
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class GrantConsentCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val alumno = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)
        val clientIp = "203.0.113.10"
        val userAgent = "test-agent/1.0"

        val consentRepository = mockk<ConsentRepository>(relaxed = true)
        val auditTrail = mockk<AuditTrail>(relaxed = true)
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val clock = MutableClock(Instant.parse("2026-08-25T10:00:00Z"))
        val command = GrantConsentCommand(consentRepository, auditTrail, eventPublisher, clock)

        beforeTest {
            clearMocks(consentRepository, auditTrail, eventPublisher)
            every { consentRepository.findLatestByUserId(club, UserId.of(alumno.userId)) } returns null
        }

        test("sin concesion previa, crea una fila nueva y publica ConsentimientoConcedido") {
            val consentSlot = slot<Consent>()
            every { consentRepository.save(capture(consentSlot)) } returns Unit
            val events = mutableListOf<Any>()
            every { eventPublisher.publishEvent(capture(events)) } returns Unit

            val result = command.execute(alumno, ConsentText.CURRENT_VERSION, clientIp, userAgent).shouldBeRight()

            result.isActive() shouldBe true
            consentSlot.captured.ip shouldBe clientIp
            consentSlot.captured.userAgent shouldBe userAgent
            events.filterIsInstance<ConsentimientoConcedido>().single().aggregateId shouldBe alumno.userId
        }

        test("una version de texto obsoleta es ConsentTextOutdated y no persiste nada") {
            command
                .execute(alumno, "v2020-01-01", clientIp, userAgent)
                .shouldBeLeft(IdentidadError.ConsentTextOutdated)

            verify(exactly = 0) { consentRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        test("si ya hay una concesion vigente, es idempotente: no crea fila ni reemite el evento") {
            val existing =
                Consent.grant(
                    UserId.of(alumno.userId),
                    club,
                    ConsentText.CURRENT_VERSION,
                    "1.2.3.4",
                    "otro-agent",
                    Instant.now(),
                )
            every { consentRepository.findLatestByUserId(club, UserId.of(alumno.userId)) } returns existing

            val result = command.execute(alumno, ConsentText.CURRENT_VERSION, clientIp, userAgent).shouldBeRight()

            result shouldBe existing
            verify(exactly = 0) { consentRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        test("tras una revocacion, volver a conceder crea una fila nueva") {
            val revoked =
                Consent
                    .grant(
                        UserId.of(alumno.userId),
                        club,
                        ConsentText.CURRENT_VERSION,
                        "1.2.3.4",
                        "otro-agent",
                        Instant.now(),
                    ).revoke(Instant.now())
            every { consentRepository.findLatestByUserId(club, UserId.of(alumno.userId)) } returns revoked
            val consentSlot = slot<Consent>()
            every { consentRepository.save(capture(consentSlot)) } returns Unit

            val result = command.execute(alumno, ConsentText.CURRENT_VERSION, clientIp, userAgent).shouldBeRight()

            result.isActive() shouldBe true
            consentSlot.captured.id shouldNotBe revoked.id
        }
    })
