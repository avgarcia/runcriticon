package com.runcriticon.seguimiento.application.usecases.plan

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanReader
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

private const val DAYS_IN_WEEK = 6L

/** Zona del club piloto (mono-club, ADR-0006 D1): fija qué día es "hoy" cerca de medianoche. */
private val CLUB_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

/**
 * La semana resuelta del propio alumno (LAL-29): lee directamente de la proyección local
 * `plan_resuelto_por_alumno`, **nunca resuelve nada en tiempo de petición** — así lo fija
 * `docs/plan-implementacion-mvp.md` para esta pantalla.
 *
 * Sin puerta `ProjectionStale` (a diferencia de `PublishPlanCommand`): ADR-0009 D9 acota el fail-closed a
 * decisiones de autorización que dependan de una proyección atrasada (autorizar la publicación de un plan
 * contra una membresía obsoleta enviaría el plan a los alumnos equivocados). Aquí solo se pinta la sesión ya
 * congelada del propio alumno — el plan de implementación acepta explícitamente el lag de esta pantalla
 * ("aceptable; el toast del entrenador lo refleja").
 *
 * Sin comprobación de relación cruzando módulos: el snapshot congelado en la propia fila *es* la prueba de
 * pertenencia al grupo que publicó el plan.
 */
@ApplicationService
class GetMyWeekQuery(
    private val reader: ResolvedPlanReader,
    private val clock: Clock,
) {
    /** [week] va aparte de [sessions]: el agregado no lo conoce (`reader.findWeek` no lo devuelve, solo las
     * sesiones que sí tienen fila), y el controller necesita el lunes resuelto para pintar `semana` en la
     * respuesta incluso una semana sin ninguna sesión publicada. */
    data class WeekResult(
        val week: LocalDate,
        val sessions: List<ResolvedSession>,
    )

    @Transactional(readOnly = true)
    fun execute(
        actor: Principal,
        week: LocalDate? = null,
    ): Either<SeguimientoError, WeekResult> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.RESOLVED_SESSION, Action.LIST)) {
                SeguimientoError.Forbidden
            }
            val monday = week ?: currentWeekMonday()
            ensure(monday.dayOfWeek == DayOfWeek.MONDAY) {
                SeguimientoError.InvalidInput(field = "semana", reason = "week_not_monday")
            }
            val sessions =
                reader.findWeek(
                    clubId = ClubId.of(actor.clubId),
                    studentId = StudentId.of(actor.userId),
                    from = monday,
                    to = monday.plusDays(DAYS_IN_WEEK),
                )
            WeekResult(week = monday, sessions = sessions)
        }

    /** El lunes de la semana en curso, en la zona del club — evitar `Clock.systemUTC()` a secas: un lunes a
     * las 00:30 en España cae todavía en domingo en UTC. */
    private fun currentWeekMonday(): LocalDate {
        val today = LocalDate.now(clock.withZone(CLUB_ZONE))
        return today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    }
}
