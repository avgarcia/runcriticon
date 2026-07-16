package com.runcriticon.shared.events

import java.util.UUID

/**
 * Garantiza el consumo idempotente de eventos de integración. Cada listener registra los `eventId` que ya procesó en la
 * tabla `{modulo}.evento_procesado(listener, event_id)`; si el mismo evento llega de nuevo (reintento, redelivery), se
 * descarta.
 */
interface ProcessedEventTracker {
    /**
     * Marks the event as processed by the given listener. Returns `true` if it was new (should be
     * processed) and `false` if it was already recorded (should be discarded).
     */
    fun markIfNew(
        listener: String,
        eventId: UUID,
    ): Boolean
}
