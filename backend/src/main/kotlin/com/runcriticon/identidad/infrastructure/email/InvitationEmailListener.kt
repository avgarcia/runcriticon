package com.runcriticon.identidad.infrastructure.email

import com.runcriticon.identidad.application.ports.EmailSender
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Conecta el outbox de Spring Modulith con el puerto [EmailSender]. Se ejecuta en una transacción
 * propia tras el commit del caso de uso (`@ApplicationModuleListener`), de modo que un fallo de
 * envío no revierte la operación de negocio; el outbox reintenta la entrega (ADR-0005, ADR-0007).
 */
@Component
class InvitationEmailListener(
    private val emailSender: EmailSender,
) {
    /** Reacciona a [InvitationEmailRequested] delegando el envío en el adaptador de email activo. */
    @ApplicationModuleListener
    fun on(event: InvitationEmailRequested) {
        emailSender.sendInvitation(event)
    }
}
