package com.runcriticon.identidad.infrastructure.email

import com.runcriticon.identidad.application.ports.EmailSender
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class InvitationEmailListener(
    private val emailSender: EmailSender,
) {
    @ApplicationModuleListener
    fun on(event: InvitationEmailRequested) {
        emailSender.sendInvitation(event)
    }
}
