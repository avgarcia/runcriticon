package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import java.time.Instant

interface EmailSender {
    fun sendInvitation(
        to: Email,
        recipientName: String,
        rawToken: RawToken,
        expiresAt: Instant,
    )
}
