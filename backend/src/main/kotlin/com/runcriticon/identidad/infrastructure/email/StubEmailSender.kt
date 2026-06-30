package com.runcriticon.identidad.infrastructure.email

import com.runcriticon.identidad.application.ports.EmailSender
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.MagicLinkEmailRequested
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Adaptador de email para el perfil `local`: no contacta con Postmark, solo registra el envío en el
 * log (ADR-0005 D14). Permite desarrollar el flujo sin credenciales ni envíos reales.
 */
@Component
@Profile("local")
class StubEmailSender : EmailSender {
    private val log = LoggerFactory.getLogger(StubEmailSender::class.java)

    /** Registra los datos de la invitación en el log en lugar de enviar un email real. */
    override fun sendInvitation(request: InvitationEmailRequested) {
        log.info(
            "[STUB-EMAIL] Invitación para {} <{}> — token={} expira={}",
            request.recipientName,
            request.to.value,
            request.rawToken.value,
            request.expiresAt,
        )
    }

    /** Registra los datos del magic link en el log en lugar de enviar un email real. */
    override fun sendMagicLink(request: MagicLinkEmailRequested) {
        log.info(
            "[STUB-EMAIL] Magic link para {} <{}> — token={} expira={}",
            request.recipientName,
            request.to.value,
            request.rawToken.value,
            request.expiresAt,
        )
    }
}
