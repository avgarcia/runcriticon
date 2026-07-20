package com.runcriticon.identidad.infrastructure.events

import com.runcriticon.identidad.application.ports.inbound.PasswordResetEmailRequested
import com.runcriticon.identidad.application.ports.outbound.notification.EmailSender
import com.runcriticon.shared.observability.MdcRestorerForEvents
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Conecta el outbox de Spring Modulith con el puerto [EmailSender] para el reseteo de contraseña. Se ejecuta en una
 * transacción propia tras el commit del caso de uso (`@ApplicationModuleListener`), de modo que un fallo de envío no
 * revierte la operación de negocio; el outbox reintenta la entrega.
 */
@Component
class PasswordResetEmailListener(
    private val emailSender: EmailSender,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    /** Reacciona a [PasswordResetEmailRequested] delegando el envío en el adaptador de email activo. */
    @ApplicationModuleListener
    fun on(event: PasswordResetEmailRequested) {
        mdcRestorer.restore(
            module = "identidad",
            traceparent = event.traceparent,
            clubId = event.clubId,
            actorId = event.actorId,
        )
        try {
            emailSender.sendPasswordReset(event)
        } finally {
            mdcRestorer.clear()
        }
    }
}
