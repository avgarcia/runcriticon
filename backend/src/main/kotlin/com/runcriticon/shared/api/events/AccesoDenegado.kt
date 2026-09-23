package com.runcriticon.shared.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Denegación de autorización (ADR-0009 D15-D16): cualquier `Either.Left(XxxError.Forbidden)` o `ProjectionStale`
 * que devuelve un caso de uso, de cualquier módulo de negocio.
 *
 * **Vive en `shared.api.events` y no en el módulo que lo publica ni en `auditoria`** — a diferencia del resto de
 * eventos del repo (cada uno vive en el módulo que lo origina), este lo produce potencialmente **cualquier**
 * módulo de negocio y solo lo consume `auditoria`, así que ninguno de los dos extremos puede ser su dueño sin
 * crear una dependencia impuesta al otro. Vivió primero en `auditoria.api.events` (al autorizar la publicación de un
 * plan a un grupo): funcionó
 * mientras solo `planificacion` lo publicaba, pero `auditoria` ya depende de `identidad` (anonimización de sus
 * asientos de auditoría al ejercer el derecho de supresión) — el día que `identidad` también necesitó
 * publicarlo, `identidad → auditoria` +
 * `auditoria → identidad` formó un ciclo que `ModulithFronterasTest` rechaza. `shared` es módulo `OPEN`
 * (exento de detección de ciclos, `SharedModule.kt`), lo que rompe la ambigüedad sin imponer una dirección.
 * `IntegrationEventArchTest` sigue satisfecho: exige un único paquete `api.events` por tipo de evento, no que
 * ese paquete cuelgue de un *bounded context* de negocio.
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
