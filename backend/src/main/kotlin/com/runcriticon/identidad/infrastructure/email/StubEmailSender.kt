package com.runcriticon.identidad.infrastructure.email

import com.runcriticon.identidad.application.ports.EmailSender
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local")
class StubEmailSender : EmailSender {
    private val log = LoggerFactory.getLogger(StubEmailSender::class.java)

    override fun sendInvitation(request: InvitationEmailRequested) {
        log.info(
            "[STUB-EMAIL] Invitación para {} <{}> — token={} expira={}",
            request.recipientName,
            request.to.value,
            request.rawToken.value,
            request.expiresAt,
        )
    }
}
