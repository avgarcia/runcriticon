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
 * [addSession]/[updateSession]/[removeSession] (LAL-24) son las únicas mutaciones de `sessions` que expone
 * el agregado. Publicar (`publish()`, LAL-25) y las operaciones sobre `personalizations` (LAL-26) llegan con
 * sus propias historias — no se exponen todavía para no dejar comportamiento sin caso de uso que lo ejerza.
 * Tampoco hay guarda de "plan ya publicado": `PlanStatus.PUBLICADO` es hoy inalcanzable (no existe
 * `publish()`), así que esa rama la añade LAL-25 junto con el estado que la hace posible.
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
        private const val WEEK_LENGTH_DAYS = 6L

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

    /**
     * Añade [session] al plan. Valida lo **relativo al plan** (LAL-24, decisión 9 del ticket): el día cae
     * dentro de la semana ([week]..[week]+6) y no hay ya una sesión ese día (`sesion_plan_dia_uk`, UNIQUE en
     * BD — este chequeo es la mitad de dominio de esa misma regla). Lo intrínseco a la sesión en sí ya lo
     * validó `Session.create`.
     */
    fun addSession(session: Session): Either<PlanificacionError, WeeklyPlan> =
        either {
            ensure(session.day in week..week.plusDays(WEEK_LENGTH_DAYS)) {
                PlanificacionError.InvalidInput(field = "dia", reason = "debe caer dentro de la semana del plan")
            }
            ensure(sessions.none { it.day == session.day }) {
                PlanificacionError.DuplicateSessionDay
            }
            copy(sessions = sessions + session)
        }

    /**
     * Sustituye la sesión con el mismo id que [session]. Sin comprobación de día duplicado: el editor no
     * permite cambiar el día de una sesión existente (LAL-24, decisión 8) — mover una sesión de día es
     * borrarla y crear otra.
     */
    fun updateSession(session: Session): Either<PlanificacionError, WeeklyPlan> =
        either {
            ensure(sessions.any { it.id == session.id }) { PlanificacionError.SessionNotFound }
            copy(sessions = sessions.map { if (it.id == session.id) session else it })
        }

    fun removeSession(sessionId: SessionId): Either<PlanificacionError, WeeklyPlan> =
        either {
            ensure(sessions.any { it.id == sessionId }) { PlanificacionError.SessionNotFound }
            copy(sessions = sessions.filterNot { it.id == sessionId })
        }
}
