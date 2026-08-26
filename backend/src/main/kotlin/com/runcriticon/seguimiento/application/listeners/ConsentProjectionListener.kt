package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.identidad.api.events.ConsentimientoConcedido
import com.runcriticon.identidad.api.events.ConsentimientoRevocado
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ConsentProjection
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Mantiene la proyección local `consentimiento_alumno` a partir de `ConsentimientoConcedido`/
 * `ConsentimientoRevocado` (LAL-128 PR2). Es lo único que permite a `SubmitSessionReportCommand` decidir sin
 * llamar síncronamente a `identidad` — ADR-0007, cada módulo mantiene su propia proyección de lo que necesita
 * de otros.
 *
 * A diferencia de `ResolvedPlanProjectionListener`, aquí **sí hace falta guarda de orden por `occurredAt`**:
 * un alumno puede conceder y revocar varias veces, y el outbox no garantiza el orden de entrega entre dos
 * eventos de agregados distintos (ni siquiera del mismo). La guarda vive en `ConsentProjectionJdbc.UPSERT_SQL`
 * (`WHERE ... last_processed_event_ts <= ?`), mismo criterio que `GroupMembersProjectionListener`.
 */
@Component
class ConsentProjectionListener(
    private val projection: ConsentProjection,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de
    // `application` dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("seguimientoProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: ConsentimientoConcedido) = apply(event, granted = true, textVersion = event.versionTexto)

    @ApplicationModuleListener
    fun on(event: ConsentimientoRevocado) = apply(event, granted = false, textVersion = REVOKED_TEXT_VERSION)

    private fun apply(
        event: IntegrationEvent,
        granted: Boolean,
        textVersion: String,
    ) {
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
            projection.upsert(
                clubId = ClubId.of(event.clubId),
                studentId = StudentId.of(event.aggregateId),
                granted = granted,
                textVersion = textVersion,
                eventId = event.eventId,
                occurredAt = event.occurredAt,
            )
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val MODULE = "seguimiento"
        const val LISTENER = "ConsentProjectionListener"

        // ConsentimientoRevocado no lleva versionTexto (no aplica: revocar no depende de una versión de
        // texto concreta) — se persiste este literal en vez de dejar la columna NULL, porque es NOT NULL y
        // no aporta nada distinguir "revocado en la versión X" de "revocado".
        const val REVOKED_TEXT_VERSION = "revocado"
    }
}
