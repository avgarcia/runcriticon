package com.runcriticon.seguimiento.application.usecases.adjustment

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.api.events.DiaReajustado
import com.runcriticon.seguimiento.application.ports.outbound.observability.SeguimientoMetrics
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ConsentReader
import com.runcriticon.seguimiento.application.ports.outbound.persistence.DayAdjustmentRepository
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanReader
import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.AdjustmentReason
import com.runcriticon.seguimiento.domain.ConflictResolution
import com.runcriticon.seguimiento.domain.DayAdjustment
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SeguimientoError
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
import java.util.UUID

private const val MAX_TARGET_DAYS_AHEAD = 7L

/** Zona del club piloto (mono-club, ADR-0006 D1), mismo criterio que `SubmitSessionReportCommand`. */
private val CLUB_ZONE: ZoneId = ZoneId.of("Europe/Madrid")

/**
 * Reajuste del día de una sesión por el propio alumno (LAL-33): la mueve a otro día (≤ +7 días) o la marca
 * como saltada, sin depender de respuesta del entrenador (`docs/research/findings.md` §P3).
 *
 * **Orden de guardas**, mismo criterio que `SubmitSessionReportCommand`: RBAC → consentimiento vigente de
 * datos de salud (el motivo `MOLESTIAS` es dato de salud, igual que en el reporte) → resolver el día efectivo
 * contra la proyección (anti-IDOR: `alumnoId` nunca es un parámetro) → rechazo de días pasados → rango de
 * destino → conflicto con el día destino → invariantes de dominio → persistencia → evento(s).
 *
 * **La proyección `plan_resuelto_por_alumno` nunca se escribe aquí**: el reajuste vive en su propia tabla,
 * superpuesta en la ruta de lectura por `ResolvedPlanReaderJdbc` (ver su KDoc) — deja intacto el snapshot
 * congelado del plan publicado (ADR-0002 D5).
 *
 * **Conflicto de día destino**: si [targetDay] ya tiene una sesión efectiva y no llega [conflictResolution],
 * falla con [SeguimientoError.TargetDayOccupied] (409) — el diálogo del alumno ofrece Reemplazar/Intercambiar/
 * Cancelar (wireframe 07 §Flujo B) y reintenta con la resolución elegida. `REEMPLAZAR`/`INTERCAMBIAR` escriben
 * dos filas que comparten `operationId` y publican un [DiaReajustado] cada una.
 */
@ApplicationService
class RescheduleDayCommand(
    private val reader: ResolvedPlanReader,
    private val repository: DayAdjustmentRepository,
    private val consentReader: ConsentReader,
    private val eventPublisher: ApplicationEventPublisher,
    private val metrics: SeguimientoMetrics,
    private val clock: Clock,
) {
    @Transactional
    fun execute(
        actor: Principal,
        day: LocalDate,
        action: AdjustmentAction,
        targetDay: LocalDate?,
        reason: AdjustmentReason,
        message: String?,
        conflictResolution: ConflictResolution?,
    ): Either<SeguimientoError, DayAdjustment> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.DAY_ADJUSTMENT, Action.RESCHEDULE)) {
                SeguimientoError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val studentId = StudentId.of(actor.userId)

            ensure(consentReader.isGranted(clubId, studentId)) {
                SeguimientoError.ConsentNotGranted
            }

            val origin = reader.findDay(clubId, studentId, day)
            ensureNotNull(origin) { SeguimientoError.SessionNotFound }

            ensureWithinRescheduleWindow(day, action, targetDay, today())

            val now = Instant.now(clock)
            val operationId = UuidCreator.getTimeOrderedEpoch()

            val originAdjustment =
                DayAdjustment
                    .create(
                        operationId = operationId,
                        action = action,
                        plannedDay = origin.plannedDay,
                        targetDay = targetDay,
                        reason = reason,
                        message = message,
                        createdAt = now,
                    ).bind()

            if (action == AdjustmentAction.SALTADA) {
                applyAndPublish(clubId, studentId, actor, origin, originAdjustment)
                return@either originAdjustment
            }

            // targetDay no es nulo aquí: la guarda de arriba lo garantiza para AdjustmentAction.MOVIDA.
            val occupant = reader.findDay(clubId, studentId, requireNotNull(targetDay))
            if (occupant == null) {
                applyAndPublish(clubId, studentId, actor, origin, originAdjustment)
                return@either originAdjustment
            }

            val occupantAdjustment =
                resolveOccupantAdjustment(operationId, occupant, day, conflictResolution, reason, message, now)

            applyAndPublish(clubId, studentId, actor, origin, originAdjustment)
            applyAndPublish(clubId, studentId, actor, occupant, occupantAdjustment)

            originAdjustment
        }

    private fun applyAndPublish(
        clubId: ClubId,
        studentId: StudentId,
        actor: Principal,
        session: ResolvedSession,
        adjustment: DayAdjustment,
    ) {
        repository.upsert(clubId, studentId, session.planId, adjustment)

        eventPublisher.publishEvent(
            DiaReajustado(
                eventId = UuidCreator.getTimeOrderedEpoch(),
                aggregateId = actor.userId,
                occurredAt = adjustment.createdAt,
                clubId = actor.clubId,
                actorId = actor.userId,
                traceparent = OpenTelemetryHelper.actualTraceparent(),
                operacionId = adjustment.operationId,
                planId = session.planId.value,
                diaPlanificado = session.plannedDay,
                accion = adjustment.action.name,
                diaDestino = adjustment.targetDay,
                motivo = adjustment.reason.name,
                marcaDolor = adjustment.painFlag,
            ),
        )
        metrics.dayRescheduled(adjustment.action)
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(CLUB_ZONE))
}

/** Reglas de fecha que dependen de "hoy": aparte de `execute` para no superar el límite de longitud de función
 * (detekt `LongMethod`). Día de origen no pasado; si es `MOVIDA`, destino obligatorio y dentro de +7 días. */
private fun Raise<SeguimientoError>.ensureWithinRescheduleWindow(
    day: LocalDate,
    action: AdjustmentAction,
    targetDay: LocalDate?,
    today: LocalDate,
) {
    ensure(!day.isBefore(today)) {
        SeguimientoError.InvalidInput(field = "dia", reason = "past_day")
    }
    if (action != AdjustmentAction.MOVIDA) return
    ensureNotNull(targetDay) {
        SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_required")
    }
    ensure(!targetDay.isBefore(today) && !targetDay.isAfter(today.plusDays(MAX_TARGET_DAYS_AHEAD))) {
        SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_out_of_range")
    }
}

/**
 * El reajuste de la sesión que ya ocupaba el día destino, tras un conflicto: `REEMPLAZAR` la marca `SALTADA`;
 * `INTERCAMBIAR` la mueve al día de origen. Sin [conflictResolution], falla con [SeguimientoError.TargetDayOccupied]
 * — el alumno decide antes de reintentar.
 */
private fun Raise<SeguimientoError>.resolveOccupantAdjustment(
    operationId: UUID,
    occupant: ResolvedSession,
    originDay: LocalDate,
    conflictResolution: ConflictResolution?,
    reason: AdjustmentReason,
    message: String?,
    now: Instant,
): DayAdjustment {
    ensureNotNull(conflictResolution) { SeguimientoError.TargetDayOccupied }
    val swap = conflictResolution == ConflictResolution.INTERCAMBIAR
    return DayAdjustment
        .create(
            operationId = operationId,
            action = if (swap) AdjustmentAction.MOVIDA else AdjustmentAction.SALTADA,
            plannedDay = occupant.plannedDay,
            targetDay = if (swap) originDay else null,
            reason = reason,
            message = message,
            createdAt = now,
        ).bind()
}
