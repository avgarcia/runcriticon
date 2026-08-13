package com.runcriticon.planificacion.application.ports.outbound.persistence

import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Comprueba contra la proyección local `miembro_grupo` (alimentada por los eventos de `club_taxonomia`, LAL-94) si
 * un entrenador tiene relación con un grupo, antes de dejarle crear un plan para él.
 *
 * **Sin bloqueo de fila ni puerta de proyección `stale`**, a diferencia de `CoachLookup`/`StudentLookup` de
 * `clubtaxonomia`: esta proyección no compite con un borrado RGPD síncrono (la persona vive en otro módulo, el
 * borrado real lo hace `club_taxonomia` sobre su propia tabla) y la política fail-closed de ADR-0009 D9 se aplaza
 * a LAL-25, donde publicar sí es la operación de verdad consecuente (recorte documentado en el README del módulo).
 */
interface CoachGroupLookup {
    /**
     * `true` solo si [coachId] aparece en `miembro_grupo` con rol `ENTRENADOR` para [groupId] dentro de [clubId].
     * Devuelve `Boolean`, no lanza `GroupNotFound`: un grupo inexistente y uno ajeno dan la misma respuesta
     * (`PlanificacionError.Forbidden`), mismo criterio que `ClubTaxonomiaError.StudentNotFound`.
     */
    fun isCoachOfGroup(
        clubId: ClubId,
        coachId: PersonId,
        groupId: GroupId,
    ): Boolean
}
