package com.runcriticon.shared.events

/**
 * Punto único de publicación de eventos de integración. La implementación apoya en el outbox de Spring Modulith
 * (`event_publication`), de modo que la publicación es transaccional con el cambio de estado que la origina (no se
 * pierde un evento si la transacción hace rollback, ni se publica si no hizo commit).
 */
interface EventPublisher {
    fun publish(event: IntegrationEvent)
}
