package com.runcriticon.identidad.application.usecases.consent

import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.testing.MutableClock
import com.runcriticon.testing.PrincipalBuilder
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant

/** Solo el ALUMNO gestiona su propio consentimiento; el rechazo no toca el puerto. */
class ConsentAuthorizationTest :
    FunSpec({
        val clock = MutableClock(Instant.parse("2026-08-25T10:00:00Z"))

        fun principal(role: Role) = PrincipalBuilder().role(role).build()

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role no puede conceder consentimiento, y no se toca el puerto") {
                val consentRepository = mockk<ConsentRepository>(relaxed = true)
                val auditTrail = mockk<AuditTrail>(relaxed = true)
                val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
                val command =
                    GrantConsentCommand(consentRepository, auditTrail, eventPublisher, clock, mockk(relaxed = true))

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
                val command =
                    RevokeConsentCommand(consentRepository, auditTrail, eventPublisher, clock, mockk(relaxed = true))

                command.execute(principal(role)).shouldBeLeft(IdentidadError.Forbidden)

                verify(exactly = 0) { consentRepository.findLatestByUserId(any(), any()) }
                verify(exactly = 0) { consentRepository.save(any()) }
            }
        }
    })
