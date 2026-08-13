package com.runcriticon.clubtaxonomia.api.events

import com.runcriticon.clubtaxonomia.application.usecases.groups.OverrideGroupMembershipCommand
import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: un alumno entra en un grupo por excepción manual. Lo publica
 * [OverrideGroupMembershipCommand] con `included = true`, dentro de su transacción. Evento simétrico a
 * [AlumnoEliminadoDeGrupo].
 *
 * **No cubre la entrada por cambio de tags** — el camino más común, que no dispara evento hoy (recorte documentado en
 * el `README.md` del módulo). `aggregateId` es el alumno, no el grupo.
 *
 * Payload mínimo — sin `name`/`email`, que el consumidor ya tiene por los eventos de `identidad`. Schema versionado en
 * `schemas/club_taxonomia/alumno-asignado-a-grupo-v1.json`, validado por el job `contractTest`.
 */
@NamedInterface("events")
data class AlumnoAsignadoAGrupo(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    /** Grupo al que entra el alumno. */
    val groupId: UUID,
) : IntegrationEvent
