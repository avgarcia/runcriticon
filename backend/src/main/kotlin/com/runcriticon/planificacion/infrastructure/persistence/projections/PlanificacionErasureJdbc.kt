package com.runcriticon.planificacion.infrastructure.persistence.projections

import com.runcriticon.planificacion.application.ports.outbound.persistence.ErasedRows
import com.runcriticon.planificacion.application.ports.outbound.persistence.PlanificacionErasure
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Adaptador de [PlanificacionErasure] sobre `JdbcTemplate`. Se invoca desde `PlanificacionDeletionListener`, que
 * corre sin principal — mismo motivo de `@NoAuthScope` que `PersonErasureJdbc` de `club_taxonomia`: la [personId]
 * ya viene resuelta del `aggregateId` del evento, no hay `clubId` de principal contra el que verificar.
 *
 * Sin FK `ON DELETE CASCADE` en el esquema (ninguna tabla del repo la usa): el orden de los `DELETE` importa —
 * `sesion`/`personalizacion` antes que `plan_semanal`, o la FK de `sesion.plan_id` rechazaría el borrado del plan.
 */
@Repository
class PlanificacionErasureJdbc(
    private val jdbc: JdbcTemplate,
) : PlanificacionErasure {
    @NoAuthScope(
        justificacion =
            "Invocado por un listener de eventos sin principal; personId ya viene resuelto del aggregateId del " +
                "evento, no hay clubId de principal contra el que verificar.",
    )
    @Transactional
    override fun erase(personId: PersonId): ErasedRows {
        // Personalizaciones del alumno (independientes del plan al que pertenezcan).
        val personalizationsAsStudent = jdbc.update(DELETE_PERSONALIZATION_BY_STUDENT_SQL, personId.value)

        // Planes cuyo entrenador es la persona borrada: primero sus hijos, luego la raíz del agregado.
        jdbc.update(DELETE_PERSONALIZATION_BY_COACH_PLANS_SQL, personId.value)
        jdbc.update(DELETE_SESSION_BY_COACH_PLANS_SQL, personId.value)
        val plans = jdbc.update(DELETE_PLAN_BY_COACH_SQL, personId.value)

        val groupMemberships = jdbc.update(DELETE_GROUP_MEMBERSHIP_SQL, personId.value)

        return ErasedRows(
            plans = plans,
            personalizations = personalizationsAsStudent,
            groupMemberships = groupMemberships,
        )
    }
}

private const val DELETE_PERSONALIZATION_BY_STUDENT_SQL =
    "DELETE FROM planificacion.personalizacion WHERE alumno_id = ?"

private const val DELETE_PERSONALIZATION_BY_COACH_PLANS_SQL =
    """
    DELETE FROM planificacion.personalizacion
    WHERE plan_id IN (SELECT id FROM planificacion.plan_semanal WHERE entrenador_id = ?)
    """

private const val DELETE_SESSION_BY_COACH_PLANS_SQL =
    """
    DELETE FROM planificacion.sesion
    WHERE plan_id IN (SELECT id FROM planificacion.plan_semanal WHERE entrenador_id = ?)
    """

private const val DELETE_PLAN_BY_COACH_SQL =
    "DELETE FROM planificacion.plan_semanal WHERE entrenador_id = ?"

private const val DELETE_GROUP_MEMBERSHIP_SQL =
    "DELETE FROM planificacion.miembro_grupo WHERE persona_id = ?"
