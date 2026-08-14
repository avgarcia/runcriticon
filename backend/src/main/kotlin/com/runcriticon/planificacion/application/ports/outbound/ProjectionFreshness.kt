package com.runcriticon.planificacion.application.ports.outbound

/**
 * Frescura de la proyección `miembro_grupo` (LAL-25, ADR-0009 D9 — puerta fail-closed a 60 s antes de publicar
 * un plan). El lag se mide sobre el outbox (`event_publication`), no sobre `last_processed_event_ts` de la
 * proyección: esa fórmula sería `now() - MAX(last_processed_event_ts)`, que en un club donde nadie toca la
 * membresía en tres días daría un lag de tres días y denegaría toda publicación. Lo correcto es medir cuánto
 * lleva pendiente la publicación incompleta más antigua dirigida a `GroupMembersProjectionListener`.
 */
interface ProjectionFreshness {
    /**
     * Segundos desde que se publicó la entrega pendiente más antigua de `MembresiaDeGrupoCambiada`; 0 si no
     * hay ninguna.
     */
    fun membersProjectionLagSeconds(): Long
}
