package com.runcriticon.seguimiento.application.usecases.report

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.api.events.ReporteRegistrado
import com.runcriticon.seguimiento.application.ports.outbound.observability.SeguimientoMetrics
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanReader
import com.runcriticon.seguimiento.application.ports.outbound.persistence.SessionReportRepository
import com.runcriticon.seguimiento.domain.NotDoneReason
import com.runcriticon.seguimiento.domain.ReportStatus
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.seguimiento.domain.SessionReport
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.observability.OpenTelemetryHelper
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Zona del club piloto (mono-club, ADR-0006 D1), igual que `GetMyWeekQuery`: fija qué día es "hoy" cerca de
 * medianoche, para rechazar reportes de días futuros con el mismo criterio que resuelve la semana en curso. */
private val CLUB_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

/**
 * Envío (o edición) del reporte de una sesión por el propio alumno (LAL-30): estado, valoración, motivo y
 * notas — la definición completa de "reporte de sesión" que fija `docs/glosario.md` §Seguimiento, sin el
 * texto libre del dolor (ver `SessionReport`).
 *
 * **Orden de guardas**, mismo criterio que `PublishPlanCommand`: RBAC → resolver el día contra la proyección
 * (anti-IDOR: `alumnoId` nunca es un parámetro, siempre `actor.userId`) → rechazo de días futuros →
 * invariantes de dominio → persistencia → evento.
 *
 * Envío idempotente: reportar dos veces el mismo día es editar, nunca crea un segundo reporte — la PK de
 * `reporte_sesion` es `(alumno_id, plan_id, dia)`.
 */
@ApplicationService
class SubmitSessionReportCommand(
    private val reader: ResolvedPlanReader,
    private val repository: SessionReportRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val metrics: SeguimientoMetrics,
    private val clock: Clock,
) {
    @Transactional
    fun execute(
        actor: Principal,
        day: LocalDate,
        status: ReportStatus,
        rating: Int?,
        reason: NotDoneReason?,
        notes: String?,
    ): Either<SeguimientoError, ResolvedSession> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.SESSION_REPORT, Action.SUBMIT)) {
                SeguimientoError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val studentId = StudentId.of(actor.userId)

            val resolved = reader.findDay(clubId, studentId, day)
            ensureNotNull(resolved) { SeguimientoError.SessionNotFound }

            ensure(!day.isAfter(today())) {
                SeguimientoError.InvalidInput(field = "dia", reason = "future_day")
            }

            val now = Instant.now(clock)
            val report = SessionReport.create(status, rating, reason, notes, now).bind()

            repository.upsert(clubId, studentId, resolved.planId, day, report)

            eventPublisher.publishEvent(
                ReporteRegistrado(
                    eventId = UuidCreator.getTimeOrderedEpoch(),
                    aggregateId = actor.userId,
                    occurredAt = now,
                    clubId = actor.clubId,
                    actorId = actor.userId,
                    traceparent = OpenTelemetryHelper.actualTraceparent(),
                    planId = resolved.planId.value,
                    dia = day,
                    estado = report.status.name,
                    valoracion = report.rating,
                    motivo = report.reason?.name,
                    marcaDolor = report.painFlag,
                ),
            )
            metrics.reportRegistered(report.status)

            resolved.copy(report = report)
        }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(CLUB_ZONE))
}
