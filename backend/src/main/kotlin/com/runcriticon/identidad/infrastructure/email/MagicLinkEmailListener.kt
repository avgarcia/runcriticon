package com.runcriticon.identidad.infrastructure.email

import com.runcriticon.identidad.application.ports.EmailSender
import com.runcriticon.identidad.application.ports.MagicLinkEmailRequested
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Conecta el outbox de Spring Modulith con el puerto [EmailSender] para el magic link. Se ejecuta en
 * una transacción propia tras el commit del caso de uso (`@ApplicationModuleListener`), de modo que un
 * fallo de envío no revierte la operación de negocio; el outbox reintenta la entrega (ADR-0005, ADR-0007).
 */
@Component
class MagicLinkEmailListener(
    private val emailSender: EmailSender,
) {
    /** Reacciona a [MagicLinkEmailRequested] delegando el envío en el adaptador de email activo. */
    @ApplicationModuleListener
    fun on(event: MagicLinkEmailRequested) {
        emailSender.sendMagicLink(event)
    }
}
