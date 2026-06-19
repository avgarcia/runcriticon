package com.runcriticon.identidad.infrastructure.email

import com.runcriticon.identidad.application.ports.EmailSender
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@Profile("local")
class StubEmailSender : EmailSender {
    private val log = LoggerFactory.getLogger(StubEmailSender::class.java)

    override fun sendInvitation(
        to: Email,
        recipientName: String,
        rawToken: RawToken,
        expiresAt: Instant,
    ) {
        log.info(
            "[STUB-EMAIL] Invitación para {} <{}> — token={} expira={}",
            recipientName,
            to.value,
            rawToken.value,
            expiresAt,
        )
    }
}
