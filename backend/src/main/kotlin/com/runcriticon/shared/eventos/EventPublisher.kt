package com.runcriticon.shared.eventos

/**
 * Punto único de publicación de eventos de integración (ADR-0005 D3, ADR-0011 D8). La
 * implementación apoya en el outbox de Spring Modulith (`event_publication`), de modo que la
 * publicación es transaccional con el cambio de estado que la origina (no se pierde un evento si
 * la transacción hace rollback, ni se publica si no hizo commit).
 *
 * El cuerpo se difiere; en H0 queda el contrato.
 */
interface EventPublisher {
    fun publish(event: IntegrationEvent)
}
