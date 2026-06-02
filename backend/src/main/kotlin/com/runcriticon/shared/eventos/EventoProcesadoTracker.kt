package com.runcriticon.shared.eventos

import java.util.UUID

/**
 * Garantiza el consumo idempotente de eventos de integración (ADR-0011 D8). Cada listener registra
 * los `eventId` que ya procesó en la tabla `{modulo}.evento_procesado(listener, event_id)`; si el
 * mismo evento llega de nuevo (reintento, redelivery), se descarta.
 *
 * El cuerpo (insert con `ON CONFLICT DO NOTHING` por módulo) se difiere; en H0 queda el contrato.
 */
interface EventoProcesadoTracker {
    /**
     * Marca el evento como procesado por el listener indicado. Devuelve `true` si era nuevo (hay
     * que procesarlo) y `false` si ya constaba (hay que descartarlo).
     */
    fun marcarSiNuevo(
        listener: String,
        eventId: UUID,
    ): Boolean
}
