package com.runcriticon.shared.events

import java.time.Instant
import java.util.UUID

/**
 * Contrato de un evento de integración entre módulos. Es la única vía de comunicación entre bounded contexts: no hay
 * llamadas síncronas cruzadas. Se publica vía outbox (`event_publication`) y lo consumen otros módulos de forma
 * idempotente.
 *
 * Los seis campos obligatorios viajan en **toda** instancia; [traceparent] propaga el contexto de traza distribuida
 * (W3C Trace Context) extremo a extremo. El JSON Schema versionado de cada evento vive en `schemas/` y su test de
 * contrato lleva `@Tag("contract")`.
 *
 * Los IDs van como `UUID` crudo a propósito: este contrato es una frontera de serialización JSON neutra entre módulos y
 * no debe acoplarse a los typed IDs de ningún módulo; el formato de las filas del outbox debe permanecer estable.
 */
interface IntegrationEvent {
    /** Identificador único del evento; clave de idempotencia para los consumidores. */
    val eventId: UUID

    /** Identificador del agregado que originó el evento. */
    val aggregateId: UUID

    /** Momento en que ocurrió el hecho de negocio (no el de publicación). */
    val occurredAt: Instant

    /** Versión del esquema del evento; permite evolución compatible. */
    val version: Int

    /** Club al que pertenece el evento (multi-tenancy y filtrado de scope). */
    val clubId: UUID

    /** Usuario que provocó el hecho; `null` si lo originó el sistema. */
    val actorId: UUID?

    /** Contexto de traza W3C (`traceparent`) para correlación distribuida; `null` si no hay traza. */
    val traceparent: String?
}
