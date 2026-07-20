package com.runcriticon.identidad.infrastructure.email

import com.runcriticon.identidad.application.ports.inbound.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.outbound.notification.EmailSender
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.infrastructure.events.InvitationEmailListener
import com.runcriticon.shared.observability.MdcRestorerForEvents
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant

class InvitationEmailListenerTest {
    private val emailSender = mockk<EmailSender>(relaxed = true)
    private val mdcRestorer = mockk<MdcRestorerForEvents>(relaxed = true)
    private val listener = InvitationEmailListener(emailSender, mdcRestorer)

    @Test
    fun `on InvitationEmailRequested calls sendInvitation with the event`() {
        val event =
            InvitationEmailRequested(
                to = Email.of("coach@example.com"),
                recipientName = "Carlos",
                rawToken = RawToken("raw-token-abc"),
                expiresAt = Instant.parse("2026-06-26T10:00:00Z"),
            )

        listener.on(event)

        verify { emailSender.sendInvitation(event) }
    }
}
