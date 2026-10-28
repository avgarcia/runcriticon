package com.runcriticon.planificacion.application.ports.outbound

import com.runcriticon.planificacion.domain.GroupId

/**
 * Frescura de la proyección `miembro_grupo` (ADR-0009 D9 — puerta fail-closed a 60 s antes de publicar
 * un plan). El lag se mide sobre el outbox (`event_publication`), no sobre `last_processed_event_ts` de la
 * proyección: esa fórmula sería `now() - MAX(last_processed_event_ts)`, que en un club donde nadie toca la
 * membresía en tres días daría un lag de tres días y denegaría toda publicación. Lo correcto es medir cuánto
 * lleva pendiente la publicación incompleta más antigua dirigida a `GroupMembersProjectionListener`.
 *
 * **La puerta va acotada al grupo** que se va a publicar: D9 deniega solo las decisiones que dependen de *esa*
 * relación, y publicar el plan de un grupo solo depende de la membresía de ese grupo. Un evento atascado de otro
 * grupo no tiene por qué impedirlo. El agregado para el gauge ([membersProjectionLagSeconds]) no se acota: mide la
 * proyección entera.
 */
interface ProjectionFreshness {
    /** Estado de las entregas pendientes de `MembresiaDeGrupoCambiada` del grupo [groupId]. */
    fun membersProjectionLag(groupId: GroupId): MembersProjectionLag

    /**
     * Segundos desde la entrega pendiente más antigua a `GroupMembersProjectionListener`, de cualquier grupo y de
     * cualquiera de los eventos que alimentan `miembro_grupo` (también los de entrenadores); 0 si no hay ninguna.
     * Cuenta también las `FAILED`: no convergen solas, y es justo lo que la alarma tiene que ver.
     *
     * No es la puerta de publicación, que sigue acotada al grupo ([membersProjectionLag]): es el agregado de sistema
     * que alimenta el gauge `planificacion.projection_lag_seconds` y su alarma por encima de 60 s (ADR-0009 D9).
     */
    fun membersProjectionLagSeconds(): Long
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
