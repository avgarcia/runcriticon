package com.runcriticon.identidad.application.usecases.consent

import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.MutableClock
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

/** Solo el ALUMNO gestiona su propio consentimiento; el rechazo no toca el puerto (LAL-128). */
class ConsentAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val clock = MutableClock(Instant.parse("2026-08-25T10:00:00Z"))

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role no puede conceder consentimiento, y no se toca el puerto") {
                val consentRepository = mockk<ConsentRepository>(relaxed = true)
                val auditTrail = mockk<AuditTrail>(relaxed = true)
                val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
                val command = GrantConsentCommand(consentRepository, auditTrail, eventPublisher, clock)

                command
                    .execute(principal(role), "v2026-08-25", "203.0.113.10", "test-agent")
                    .shouldBeLeft(IdentidadError.Forbidden)

                verify(exactly = 0) { consentRepository.findLatestByUserId(any(), any()) }
                verify(exactly = 0) { consentRepository.save(any()) }
            }

            test("$role no puede revocar consentimiento, y no se toca el puerto") {
                val consentRepository = mockk<ConsentRepository>(relaxed = true)
                val auditTrail = mockk<AuditTrail>(relaxed = true)
                val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
                val command = RevokeConsentCommand(consentRepository, auditTrail, eventPublisher, clock)

                command.execute(principal(role)).shouldBeLeft(IdentidadError.Forbidden)

                verify(exactly = 0) { consentRepository.findLatestByUserId(any(), any()) }
                verify(exactly = 0) { consentRepository.save(any()) }
            }
        }
    })
