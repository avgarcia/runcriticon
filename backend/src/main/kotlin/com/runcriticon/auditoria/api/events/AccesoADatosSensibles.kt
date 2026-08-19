package com.runcriticon.auditoria.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Acceso efectivo a datos sensibles de un tercero (ADR-0009 D15: salud, perfil personal) — `@AuditaAcceso` en el
 * caso de uso que lee o modifica esos datos. Solo se publica cuando la operación tiene éxito (`Either.Right`); un
 * intento fallido es [AccesoDenegado], no esto.
 *
 * Mismo motivo que [AccesoDenegado] para vivir en `auditoria.api.events` en vez de en el módulo productor.
 *
 * **Sin productor todavía**: el módulo `seguimiento` (dueño de los datos de salud) no existe en este repo — el
 * evento y el consumidor quedan listos para cuando llegue, sin instrumentar ningún caso de uso ficticio.
 */
@NamedInterface("events")
data class AccesoADatosSensibles(
    override val eventId: UUID,
    /** El sujeto cuyos datos se leyeron o modificaron — a diferencia de [AccesoDenegado], aquí siempre hay uno. */
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    /** Qué se leyó/modificó, ej. `"alumno_perfil"`, `"marca"`. */
    val recurso: String,
    val sujetoId: UUID,
) : IntegrationEvent
