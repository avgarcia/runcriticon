package com.runcriticon.seguimiento.application.usecases.alerts

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.seguimiento.application.SeguimientoAccessAuditor
import com.runcriticon.seguimiento.application.ports.outbound.persistence.CoachAlertReader
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ConsentReader
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
 * Panel de alertas del entrenador (M17): lee de [CoachAlertReader], computado a petición contra
 * `reporte_sesion`/`plan_resuelto_por_alumno` — sin tabla de alertas ni de descartadas, el panel es de solo
 * lectura (ver el KDoc de [CoachAlert]).
 *
 * `@AuditAccess` (ADR-0009 D15): primer caso de uso de `seguimiento` que expone datos de un tercero (el
 * entrenador lee reportes de sus alumnos) — [Result] implementa [AuditSubjects] con un id por alumno con
 * alerta activa, así que [com.runcriticon.shared.rgpd.AuditAccessAspect] publica un `AccesoADatosSensibles`
 * por cada uno.
 *
 * **Consentimiento vigente** (ADR-0014 D18): revocarlo corta también la lectura de lo ya recogido — tras
 * revocar, el tratamiento dependiente cesa, y que el entrenador vea el dolor o la lesión del alumno es una
 * operación de tratamiento nueva. Se descarta **al alumno entero**, no solo sus alertas de salud: "sin
 * reportar" también delata — quien revoca ya no puede reportar, así que esa alerta sería falsa y permanente,
 * y se calcula sobre la fecha de sus reportes. Como el [Result] sale ya filtrado, tampoco se audita un
 * acceso que no ha ocurrido.
 *
 * **`@Transactional` sin `readOnly = true`** (necesario para la auditoría de acceso a datos sensibles) pese a
 * que el caso de uso solo lee de
 * `CoachAlertReader`: el propio `@AuditAccess` escribe en el outbox dentro de esta misma transacción, y
 * `readOnly = true` se propaga a la conexión JDBC — ver el KDoc de
 * [com.runcriticon.shared.rgpd.AuditAccessAspect] para el porqué exacto y cómo se detectó.
 */
@ApplicationService
class ListCoachAlertsQuery(
    private val reader: CoachAlertReader,
    private val consentReader: ConsentReader,
    private val clock: Clock,
    private val auditor: SeguimientoAccessAuditor,
) {
    /** Envoltorio propio del caso de uso (mismo criterio que `GetMyWeekQuery.WeekResult`): implementa
     * [AuditSubjects] para que el aspecto de auditoría sepa qué sujetos alcanzó esta lectura. */
    data class Result(
        val alerts: List<CoachAlert>,
    ) : AuditSubjects {
        override fun auditSubjectIds(): Set<UUID> = alerts.mapTo(mutableSetOf()) { it.studentId.value }
    }

    @AuditAccess(type = AccessType.SALUD, resource = RECURSO_ALERTAS)
    @Transactional
    fun execute(
        actor: Principal,
        groupId: UUID? = null,
    ): Either<SeguimientoError, Result> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.COACH_ALERT, Action.LIST)) {
                auditor.rbacDenied(actor, Resource.COACH_ALERT, Action.LIST)
                SeguimientoError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val alerts =
                reader.findActiveAlerts(
                    clubId = clubId,
                    coachId = CoachId.of(actor.userId),
                    groupId = groupId?.let { GroupId.of(it) },
                    today = LocalDate.now(clock.withZone(CLUB_ZONE)),
                )
            Result(alerts.ofStudentsWithConsent(clubId))
        }

    /** Ver "Consentimiento vigente" en el KDoc de la clase. */
    private fun List<CoachAlert>.ofStudentsWithConsent(clubId: ClubId): List<CoachAlert> {
        if (isEmpty()) return this
        val granted = consentReader.grantedAmong(clubId, mapTo(mutableSetOf()) { it.studentId })
        return filter { it.studentId in granted }
    }
}
