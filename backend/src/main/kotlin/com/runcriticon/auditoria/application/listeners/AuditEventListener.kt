package com.runcriticon.auditoria.application.listeners

import com.runcriticon.auditoria.api.events.AccesoADatosSensibles
import com.runcriticon.auditoria.api.events.AccesoDenegado
import com.runcriticon.auditoria.application.ports.outbound.observability.AuditEventMetrics
import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventRepository
import com.runcriticon.auditoria.domain.AuditEvent
import com.runcriticon.auditoria.domain.AuditEventId
import com.runcriticon.auditoria.domain.AuditEventType
import com.runcriticon.auditoria.infrastructure.persistence.events.AuditoriaProcessedEventTracker
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Persiste en `auditoria.evento` los dos eventos de autorización (ADR-0009 D15-D17). Es el único trabajo de este
 * módulo: no decide, no es invocado síncronamente — un consumidor más del outbox, como cualquier otro.
 */
@Component
class AuditEventListener(
    private val repository: AuditEventRepository,
    @Qualifier(AuditoriaProcessedEventTracker.QUALIFIER)
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
    private val metrics: AuditEventMetrics,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: AccesoDenegado) =
        persist(
            listenerKey = "$LISTENER.AccesoDenegado",
            eventId = event.eventId,
            clubId = event.clubId,
            actorId = event.actorId,
            traceparent = event.traceparent,
        ) {
            AuditEvent(
                id = AuditEventId.new(),
                clubId = ClubId.of(event.clubId),
                type = AuditEventType.ACCESO_DENEGADO,
                actorId = event.actorId,
                sujetoId = event.sujetoId,
                recurso = event.recurso,
                motivo = event.motivo,
                occurredAt = event.occurredAt,
            )
        }

    @ApplicationModuleListener
    fun on(event: AccesoADatosSensibles) =
        persist(
            listenerKey = "$LISTENER.AccesoADatosSensibles",
            eventId = event.eventId,
            clubId = event.clubId,
            actorId = event.actorId,
            traceparent = event.traceparent,
        ) {
            AuditEvent(
                id = AuditEventId.new(),
                clubId = ClubId.of(event.clubId),
                type = AuditEventType.ACCESO_DATOS_SENSIBLES,
                actorId = event.actorId,
                sujetoId = event.sujetoId,
                recurso = event.recurso,
                motivo = null,
                occurredAt = event.occurredAt,
            )
        }

    private fun persist(
        listenerKey: String,
        eventId: UUID,
        clubId: UUID,
        actorId: UUID?,
        traceparent: String?,
        build: () -> AuditEvent,
    ) {
        mdcRestorer.restore(module = MODULE, traceparent = traceparent, clubId = clubId, actorId = actorId)
        try {
            if (!processedEvents.markIfNew(listenerKey, eventId)) {
                log.debug("Evento {} ya procesado por {}; se descarta", eventId, listenerKey)
                return
            }
            val auditEvent = build()
            repository.save(auditEvent)
            metrics.recorded(auditEvent.type)
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val MODULE = "auditoria"
        const val LISTENER = "AuditEventListener"
    }
}
