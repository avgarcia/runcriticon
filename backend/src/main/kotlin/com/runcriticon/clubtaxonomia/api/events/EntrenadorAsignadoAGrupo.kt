package com.runcriticon.clubtaxonomia.api.events

import com.runcriticon.clubtaxonomia.application.usecases.groups.AssignCoachToGroupCommand
import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: un entrenador queda vinculado a un grupo. Lo publica [AssignCoachToGroupCommand] dentro
 * de su transacción. Evento simétrico a [EntrenadorEliminadoDeGrupo]. `aggregateId` es el entrenador, no el grupo.
 *
 * Payload mínimo — sin `name`/`email`, que el consumidor ya tiene por los eventos de `identidad`. Schema versionado en
 * `schemas/club_taxonomia/entrenador-asignado-a-grupo-v1.json`, validado por el job `contractTest`.
 */
@NamedInterface("events")
data class EntrenadorAsignadoAGrupo(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    /** Grupo al que queda vinculado el entrenador. */
    val groupId: UUID,
) : IntegrationEvent
