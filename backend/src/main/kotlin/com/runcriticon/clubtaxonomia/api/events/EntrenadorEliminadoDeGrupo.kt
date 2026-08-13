package com.runcriticon.clubtaxonomia.api.events

import com.runcriticon.clubtaxonomia.application.usecases.groups.UnassignCoachFromGroupCommand
import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: un entrenador queda desvinculado de un grupo. Lo publica
 * [UnassignCoachFromGroupCommand] dentro de su transacción. Evento simétrico a [EntrenadorAsignadoAGrupo].
 * `aggregateId` es el entrenador, no el grupo.
 *
 * Payload mínimo — sin `name`/`email`, que el consumidor ya tiene por los eventos de `identidad`. Schema versionado en
 * `schemas/club_taxonomia/entrenador-eliminado-de-grupo-v1.json`, validado por el job `contractTest`.
 */
@NamedInterface("events")
data class EntrenadorEliminadoDeGrupo(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    /** Grupo del que queda desvinculado el entrenador. */
    val groupId: UUID,
) : IntegrationEvent
