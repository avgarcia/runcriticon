package com.runcriticon.planificacion.application.ports.outbound

import com.runcriticon.planificacion.domain.GroupId

/**
 * Frescura de la proyección `miembro_grupo` (ADR-0009 D9 — puerta fail-closed a 60 s antes de publicar
 * un plan). El lag se mide sobre el outbox (`event_publication`), no sobre `last_processed_event_ts` de la
 * proyección: esa fórmula sería `now() - MAX(last_processed_event_ts)`, que en un club donde nadie toca la
 * membresía en tres días daría un lag de tres días y denegaría toda publicación. Lo correcto es medir cuánto
 * lleva pendiente la publicación incompleta más antigua dirigida a `GroupMembersProjectionListener`.
 *
 * **Acotado al grupo** que se va a publicar: D9 deniega solo las decisiones que dependen de *esa* relación, y
 * publicar el plan de un grupo solo depende de la membresía de ese grupo. Un evento atascado de otro grupo no tiene
 * por qué impedirlo.
 */
interface ProjectionFreshness {
    /** Estado de las entregas pendientes de `MembresiaDeGrupoCambiada` del grupo [groupId]. */
    fun membersProjectionLag(groupId: GroupId): MembersProjectionLag
}

/**
 * @property seconds segundos desde la entrega pendiente más antigua del grupo; 0 si no hay ninguna.
 * @property failed alguna está marcada `FAILED` por el outbox (`spring.modulith.events.staleness`): no va a
 * converger sola, hace falta que alguien la reprocese (ADR-0007 D13).
 */
data class MembersProjectionLag(
    val seconds: Long,
    val failed: Boolean,
)
