package com.runcriticon.auditoria.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Denegación de autorización (ADR-0009 D15-D16): cualquier `Either.Left(XxxError.Forbidden)` o `ProjectionStale`
 * que devuelve un caso de uso, de cualquier módulo de negocio.
 *
 * **Vive en `auditoria.api.events` y no en el módulo que lo publica** — a diferencia del resto de eventos del
 * repo (cada uno vive en el módulo que lo origina), este lo produce potencialmente **cualquier** módulo de
 * negocio, y `IntegrationEventArchTest` exige un único paquete `api.events` por tipo de evento. `auditoria` es
 * el único consumidor estable, así que su paquete es el contrato público que cada productor importa —
 * `planificacion.PublishPlanCommand` es el primero (LAL-93 AC3); el resto de casos de uso `Forbidden` del repo
 * lo harán cuando les toque, ticket aparte.
 *
 * Se publica en la **misma transacción** que la operación denegada (D16): si Postgres falla al persistir el
 * evento, la operación también falla — no existe el caso "denegación sin rastro".
 */
@NamedInterface("events")
data class AccesoDenegado(
    override val eventId: UUID,
    /** ID del recurso al que se intentó acceder; si la denegación no llega a identificar un recurso (p. ej. RBAC
     * puro, sin haber cargado nada todavía), el propio [actorId]. */
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    /** Recurso y acción de la matriz de autorización, ej. `"PLAN:PUBLISH"`. */
    val recurso: String,
    /** Motivo de la denegación para investigación forense — nunca viaja al cliente (D12: el HTTP 403 es neutro). */
    val motivo: String,
    /** Tercero sobre el que recaía la operación denegada, cuando lo hay (p. ej. el alumno de un plan ajeno). */
    val sujetoId: UUID? = null,
) : IntegrationEvent
