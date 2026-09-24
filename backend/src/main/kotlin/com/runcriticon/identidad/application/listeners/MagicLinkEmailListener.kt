package com.runcriticon.identidad.application.listeners

import com.runcriticon.identidad.application.ports.inbound.MagicLinkEmailRequested
import com.runcriticon.identidad.application.ports.outbound.notification.EmailSender
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Conecta el outbox de Spring Modulith con el puerto [EmailSender] para el magic link. Se ejecuta en una transacción
 * propia tras el commit del caso de uso (`@ApplicationModuleListener`), de modo que un fallo de envío no revierte la
 * operación de negocio; el outbox reintenta la entrega — de ahí [ProcessedEventTracker]: sin él, una reentrega
 * reenvía el email (LAL-144).
 */
@Component
class MagicLinkEmailListener(
    private val emailSender: EmailSender,
    private val mdcRestorer: MdcRestorerForEvents,
    @Qualifier("identidadProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Reacciona a [MagicLinkEmailRequested] delegando el envío en el adaptador de email activo. */
    @ApplicationModuleListener
    fun on(event: MagicLinkEmailRequested) {
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
            emailSender.sendMagicLink(event)
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val LISTENER = "MagicLinkEmailListener"
    }
}
