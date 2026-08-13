package com.runcriticon.planificacion.infrastructure.persistence.projections

import com.runcriticon.planificacion.application.ports.outbound.persistence.CoachGroupLookup
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Adaptador de [CoachGroupLookup] sobre `JdbcTemplate`, contra la proyección `miembro_grupo` (LAL-94 vía
 * `GroupMembersProjectionListener`). Sin `@Entity`, mismo motivo que el resto de proyecciones del repo: es una
 * comprobación puntual, no un agregado.
 */
@Repository
class CoachGroupLookupJdbc(
    private val jdbc: JdbcTemplate,
) : CoachGroupLookup {
    @AuthScope(Scope.CLUB)
    override fun isCoachOfGroup(
        clubId: ClubId,
        coachId: PersonId,
        groupId: GroupId,
    ): Boolean =
        jdbc.queryForObject(
            IS_COACH_OF_GROUP_SQL,
            Boolean::class.java,
            groupId.value,
            clubId.value,
            coachId.value,
            ROLE_ENTRENADOR,
        ) ?: false
}

private const val ROLE_ENTRENADOR = "ENTRENADOR"

private const val IS_COACH_OF_GROUP_SQL =
    """
    SELECT EXISTS (
        SELECT 1 FROM planificacion.miembro_grupo
        WHERE grupo_id = ? AND club_id = ? AND persona_id = ? AND rol = ?
    )
    """
