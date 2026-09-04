package com.runcriticon.seguimiento.application.usecases.alerts

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.seguimiento.application.ports.outbound.persistence.CoachAlertReader
import com.runcriticon.seguimiento.domain.CoachAlert
import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.rgpd.AccessType
import com.runcriticon.shared.rgpd.AuditAccess
import com.runcriticon.shared.rgpd.AuditSubjects
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Zona del club piloto (mono-club, ADR-0006 D1), mismo criterio que `GetMyWeekQuery.CLUB_ZONE`. */
private val CLUB_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

private const val RECURSO_ALERTAS = "reporte_sesion"

/**
 * Panel de alertas del entrenador (LAL-116, M17): lee de [CoachAlertReader], computado a petición contra
 * `reporte_sesion`/`plan_resuelto_por_alumno` — sin tabla de alertas ni de descartadas, el panel es de solo
 * lectura (ver el KDoc de [CoachAlert]).
 *
 * `@AuditAccess` (ADR-0009 D15): primer caso de uso de `seguimiento` que expone datos de un tercero (el
 * entrenador lee reportes de sus alumnos) — [Result] implementa [AuditSubjects] con un id por alumno con
 * alerta activa, así que [com.runcriticon.shared.rgpd.AuditAccessAspect] publica un `AccesoADatosSensibles`
 * por cada uno.
 */
@ApplicationService
class ListCoachAlertsQuery(
    private val reader: CoachAlertReader,
    private val clock: Clock,
) {
    /** Envoltorio propio del caso de uso (mismo criterio que `GetMyWeekQuery.WeekResult`): implementa
     * [AuditSubjects] para que el aspecto de auditoría sepa qué sujetos alcanzó esta lectura. */
    data class Result(
        val alerts: List<CoachAlert>,
    ) : AuditSubjects {
        override fun auditSubjectIds(): Set<UUID> = alerts.mapTo(mutableSetOf()) { it.studentId.value }
    }

    @AuditAccess(type = AccessType.SALUD, resource = RECURSO_ALERTAS)
    @Transactional(readOnly = true)
    fun execute(
        actor: Principal,
        groupId: UUID? = null,
    ): Either<SeguimientoError, Result> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.COACH_ALERT, Action.LIST)) {
                SeguimientoError.Forbidden
            }
            val alerts =
                reader.findActiveAlerts(
                    clubId = ClubId.of(actor.clubId),
                    coachId = CoachId.of(actor.userId),
                    groupId = groupId?.let { GroupId.of(it) },
                    today = LocalDate.now(clock.withZone(CLUB_ZONE)),
                )
            Result(alerts)
        }
}
