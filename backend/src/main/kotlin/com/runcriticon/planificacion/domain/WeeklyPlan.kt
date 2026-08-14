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
 * el agregado. [publish] (LAL-25) las congela: una vez `PUBLICADO`, las tres rechazan con
 * [PlanificacionError.PlanAlreadyPublished] — el wireframe promete cambios en tiempo real tras publicar, pero
 * eso exige eventos de modificación y un consumidor en Seguimiento que no existen, y rompería la congelación
 * de membresía de ADR-0002 D5. Las operaciones sobre `personalizations` (LAL-26) llegan con su propia historia.
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
            ensure(status == PlanStatus.BORRADOR) { PlanificacionError.PlanAlreadyPublished }
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
            ensure(status == PlanStatus.BORRADOR) { PlanificacionError.PlanAlreadyPublished }
            ensure(sessions.any { it.id == session.id }) { PlanificacionError.SessionNotFound }
            copy(sessions = sessions.map { if (it.id == session.id) session else it })
        }

    fun removeSession(sessionId: SessionId): Either<PlanificacionError, WeeklyPlan> =
        either {
            ensure(status == PlanStatus.BORRADOR) { PlanificacionError.PlanAlreadyPublished }
            ensure(sessions.any { it.id == sessionId }) { PlanificacionError.SessionNotFound }
            copy(sessions = sessions.filterNot { it.id == sessionId })
        }

    /**
     * Publica el plan al grupo (LAL-25): congela la membresía resuelta en este momento (ADR-0002 D5) — el
     * snapshot en sí lo resuelve el caso de uso consultando la proyección de grupos, no el agregado, que no
     * conoce la membresía. Un plan sin sesiones no se puede publicar: publicar una semana en blanco es
     * siempre un error del entrenador, nunca un estado válido.
     */
    fun publish(): Either<PlanificacionError, WeeklyPlan> =
        either {
            ensure(status == PlanStatus.BORRADOR) { PlanificacionError.PlanAlreadyPublished }
            ensure(sessions.isNotEmpty()) { PlanificacionError.NoSessions }
            copy(status = PlanStatus.PUBLICADO)
        }
}
