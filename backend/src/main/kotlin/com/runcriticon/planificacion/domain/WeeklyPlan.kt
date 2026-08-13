package com.runcriticon.planificacion.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.shared.tenancy.ClubId
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Plan semanal de un grupo: la raíz del agregado, con [sessions] y [personalizations] como entidades hijas
 * (ADR-0002 D9, ADR-0008 D17 — carga eager, siempre las dos colecciones completas).
 *
 * Este ticket (LAL-114) solo construye el borrador; publicar (`publish()`, LAL-25) y las operaciones que
 * mutan `sessions`/`personalizations` llegan con sus propias historias — el agregado no las expone todavía
 * para no dejar comportamiento sin caso de uso que lo ejerza.
 */
data class WeeklyPlan(
    val id: PlanId,
    val clubId: ClubId,
    val groupId: GroupId,
    val coachId: PersonId,
    val week: LocalDate,
    val status: PlanStatus,
    val sessions: List<Session> = emptyList(),
    val personalizations: List<Personalization> = emptyList(),
) {
    companion object {
        /**
         * Crea el plan en borrador. [week] debe ser el lunes de la semana: es la convención de la que cuelgan las
         * sesiones (LAL-24), y aceptar cualquier día dejaría ambigua a qué semana pertenece un plan cuya fecha cae
         * a mitad de semana.
         */
        fun createDraft(
            clubId: ClubId,
            groupId: GroupId,
            coachId: PersonId,
            week: LocalDate,
            id: PlanId = PlanId.new(),
        ): Either<PlanificacionError, WeeklyPlan> =
            either {
                ensure(week.dayOfWeek == DayOfWeek.MONDAY) {
                    PlanificacionError.InvalidInput(field = "semana", reason = "debe ser el lunes de la semana")
                }
                WeeklyPlan(
                    id = id,
                    clubId = clubId,
                    groupId = groupId,
                    coachId = coachId,
                    week = week,
                    status = PlanStatus.BORRADOR,
                )
            }
    }
}
