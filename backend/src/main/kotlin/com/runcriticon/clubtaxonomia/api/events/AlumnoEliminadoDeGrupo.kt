package com.runcriticon.clubtaxonomia.api.events

import com.runcriticon.clubtaxonomia.application.usecases.groups.OverrideGroupMembershipCommand
import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: un alumno sale de un grupo por excepción manual. Lo publica
 * [OverrideGroupMembershipCommand] con `included = false`, dentro de su transacción. Evento simétrico a
 * [AlumnoAsignadoAGrupo].
 *
 * **No cubre la salida por quitar la excepción manual**
 * ([com.runcriticon.clubtaxonomia.application.usecases.groups.ClearGroupMembershipOverrideCommand]) ni por cambio de
 * tags: en ambos casos el alumno puede seguir dentro del grupo según el filtro vigente, y ese cálculo no entra en
 * este evento (recorte documentado en el `README.md` del módulo). `aggregateId` es el alumno, no el grupo.
 *
 * Payload mínimo — sin `name`/`email`, que el consumidor ya tiene por los eventos de `identidad`. Schema versionado en
 * `schemas/club_taxonomia/alumno-eliminado-de-grupo-v1.json`, validado por el job `contractTest`.
 */
@NamedInterface("events")
data class AlumnoEliminadoDeGrupo(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    /** Grupo del que sale el alumno. */
    val groupId: UUID,
) : IntegrationEvent
