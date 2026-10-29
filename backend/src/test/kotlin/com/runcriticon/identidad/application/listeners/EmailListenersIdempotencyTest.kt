package com.runcriticon.identidad.application.listeners

import com.runcriticon.identidad.application.ports.inbound.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.inbound.MagicLinkEmailRequested
import com.runcriticon.identidad.application.ports.inbound.PasswordResetEmailRequested
import com.runcriticon.identidad.application.ports.outbound.notification.EmailSender
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.testing.ConstantUserIdHasher
import com.runcriticon.testing.InMemoryProcessedEventTracker
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

/**
 * Un solo envío aunque el outbox reentregue el mismo evento (ADR-0007 D9): los tres listeners de email
 * comparten el mismo [InMemoryProcessedEventTracker] que ya usan `MergeSuggestionListenerTest`/
 * `PersonProjectionListenerTest` — mismo contrato exacto de `evento_procesado`.
 */
class EmailListenersIdempotencyTest :
    FunSpec({
        val mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher)

        test("InvitationEmailListener: reentregar el mismo evento no reenvía la invitación") {
            val emailSender = mockk<EmailSender>(relaxed = true)
            val processedEvents = InMemoryProcessedEventTracker()
            val listener = InvitationEmailListener(emailSender, mdcRestorer, processedEvents)
            val event =
                InvitationEmailRequested(
                    to = Email.of("coach@example.com"),
                    recipientName = "Carlos",
                    rawToken = RawToken("raw-token-abc"),
                    expiresAt = Instant.parse("2026-06-26T10:00:00Z"),
                )

            listener.on(event)
            listener.on(event) // reentrega del outbox

            verify(exactly = 1) { emailSender.sendInvitation(event) }
        }

        test("MagicLinkEmailListener: reentregar el mismo evento no reenvía el magic link") {
            val emailSender = mockk<EmailSender>(relaxed = true)
            val processedEvents = InMemoryProcessedEventTracker()
            val listener = MagicLinkEmailListener(emailSender, mdcRestorer, processedEvents)
            val event =
                MagicLinkEmailRequested(
                    to = Email.of("alumno@example.com"),
                    recipientName = "Ana",
                    rawToken = RawToken("raw-token-def"),
                    expiresAt = Instant.parse("2026-06-26T10:15:00Z"),
                )

            listener.on(event)
            listener.on(event)

            verify(exactly = 1) { emailSender.sendMagicLink(event) }
        }

        test("PasswordResetEmailListener: reentregar el mismo evento no reenvía el reseteo") {
            val emailSender = mockk<EmailSender>(relaxed = true)
            val processedEvents = InMemoryProcessedEventTracker()
            val listener = PasswordResetEmailListener(emailSender, mdcRestorer, processedEvents)
            val event =
                PasswordResetEmailRequested(
                    to = Email.of("entrenador@example.com"),
                    recipientName = "Elena",
                    rawToken = RawToken("raw-token-ghi"),
                    expiresAt = Instant.parse("2026-06-26T10:30:00Z"),
                )

            listener.on(event)
            listener.on(event)

            verify(exactly = 1) { emailSender.sendPasswordReset(event) }
        }
    })
