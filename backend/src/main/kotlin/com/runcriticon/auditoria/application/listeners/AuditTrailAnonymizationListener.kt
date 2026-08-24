package com.runcriticon.auditoria.application.listeners

import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventRepository
import com.runcriticon.auditoria.infrastructure.persistence.events.AuditoriaProcessedEventTracker
import com.runcriticon.identidad.api.events.AdminEliminado
import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Aplica el derecho al olvido sobre `auditoria.evento` (ADR-0009 D17): **anonimiza** (`actor_id`/`sujeto_id` →
 * `NULL`), no borra físicamente — a diferencia de `StudentDeletionListener` de los demás módulos, porque este es
 * precisamente el rastro de auditoría que debe sobrevivir a la persona que menciona (categoría RGPD
 * `AUDITORIA_AUTORIZACION`, patrón de borrado mixto de ADR-0014).
 *
 * Cubre las **tres** bajas (alumno, entrenador y, desde LAL-126, admin): los tres pueden aparecer como `actorId` de
 * un asiento `ACCESO_DENEGADO`/`ACCESO_DATOS_SENSIBLES` (un admin sí puede ver denegado un acceso), y alumno/
 * entrenador también como `sujetoId`.
 */
@Component
class AuditTrailAnonymizationListener(
    private val repository: AuditEventRepository,
    @Qualifier(AuditoriaProcessedEventTracker.QUALIFIER)
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: AlumnoEliminado) = anonymize(event)

    @ApplicationModuleListener
    fun on(event: EntrenadorEliminado) = anonymize(event)

    @ApplicationModuleListener
    fun on(event: AdminEliminado) = anonymize(event)

    private fun anonymize(event: IntegrationEvent) {
        mdcRestorer.restore(
            module = MODULE,
            traceparent = event.traceparent,
            clubId = event.clubId,
            actorId = event.actorId,
        )
        try {
            if (!processedEvents.markIfNew(LISTENER, event.eventId)) {
                log.debug("Evento {} ya procesado por {}; se descarta", event.eventId, LISTENER)
                return
            }
            val filas = repository.anonymize(event.aggregateId)
            log.info("Anonimización aplicada: {} filas de auditoria.evento", filas)
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val MODULE = "auditoria"
        const val LISTENER = "AuditTrailAnonymizationListener"
    }
}
