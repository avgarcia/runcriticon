package com.runcriticon.planificacion.application.ports.outbound.persistence

import com.runcriticon.planificacion.domain.PersonId

/**
 * Puerto de borrado físico de todo lo que este módulo guarda sobre una persona, al ejercer ella su derecho de
 * supresión. Mismo criterio que `PersonErasure` de `club_taxonomia`: un único método sirve para ambos roles —
 * el MVP fija un rol único por usuario (ADR-0003), así que [personId] solo puede aparecer como `entrenador_id`
 * o como `alumno_id`, nunca los dos.
 */
interface PlanificacionErasure {
    /**
     * Borra:
     *  - Los planes cuyo `entrenador_id` sea [personId], en cascada con sus sesiones, personalizaciones y
     *    snapshots de membresía (borrar al entrenador dueño se lleva su plan entero — no hay "anonimizar la
     *    raíz del agregado" en este módulo).
     *  - Las personalizaciones cuyo `alumno_id` sea [personId], sin tocar el plan ni sus otras sesiones.
     *  - Las filas de `plan_snapshot_alumno` cuyo `alumno_id` sea [personId] (LAL-25): borrado físico, mismo
     *    criterio que `personalizacion` — un alumno borrado no debe seguir apareciendo en el snapshot congelado
     *    de ningún plan, publicado o no.
     *  - Las filas de `miembro_grupo` cuyo `persona_id` sea [personId].
     *
     * Idempotente: repetirlo sobre alguien ya borrado no falla y deja el mismo estado. Válido también sobre una
     * persona que este módulo nunca llegó a proyectar.
     */
    fun erase(personId: PersonId): ErasedRows
}

/** Recuento de lo borrado para una persona. */
data class ErasedRows(
    val plans: Int,
    val personalizations: Int,
    val groupMemberships: Int,
    val snapshotEntries: Int,
)
