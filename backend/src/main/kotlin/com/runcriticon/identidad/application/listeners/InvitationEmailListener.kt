package com.runcriticon.identidad.application.listeners

import com.runcriticon.identidad.application.ports.inbound.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.outbound.notification.EmailSender
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Conecta el outbox de Spring Modulith con el puerto [EmailSender]. Se ejecuta en una transacción propia tras el commit
 * del caso de uso (`@ApplicationModuleListener`), de modo que un fallo de envío no revierte la operación de negocio; el
 * outbox reintenta la entrega — de ahí [ProcessedEventTracker]: sin él, una reentrega reenvía el email.
 */
@Component
class InvitationEmailListener(
    private val emailSender: EmailSender,
    private val mdcRestorer: MdcRestorerForEvents,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de
    // `application` dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("identidadProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Reacciona a [InvitationEmailRequested] delegando el envío en el adaptador de email activo. */
    @ApplicationModuleListener
    fun on(event: InvitationEmailRequested) {
        mdcRestorer.restore(
            module = "identidad",
            traceparent = event.traceparent,
            clubId = event.clubId,
            actorId = event.actorId,
        )
        try {
            if (!processedEvents.markIfNew(LISTENER, event.eventId)) {
                log.debug("Evento {} ya procesado por {}; se descarta", event.eventId, LISTENER)
                return
            }
            emailSender.sendInvitation(event)
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val LISTENER = "InvitationEmailListener"
    }
}
