package com.runcriticon.planificacion.application.usecases.personalizations

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.auditoria.api.events.AccesoDenegado
import com.runcriticon.planificacion.api.events.PersonalizacionRetirada
import com.runcriticon.planificacion.application.ports.outbound.persistence.CoachGroupLookup
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanStatus
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.observability.OpenTelemetryHelper
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

private const val RECURSO_PERSONALIZAR = "PLAN:PERSONALIZE"

/**
 * Retira la personalización de un alumno en una sesión (LAL-26). No idempotente a nivel de dominio — si no
 * existía devuelve [PlanificacionError.PersonalizationNotFound]: a diferencia de `quitarAjusteDePertenencia`
 * de `club_taxonomia` (204 silencioso), aquí el caso de uso ya cargó el plan entero y distinguirlo no cuesta
 * nada extra; el mapeo REST decide si lo expone como 404 o lo trata como éxito.
 */
@ApplicationService
class RemovePersonalizationCommand(
    private val repository: WeeklyPlanRepository,
    private val coachGroupLookup: CoachGroupLookup,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun execute(
        actor: Principal,
        planId: PlanId,
        sessionId: SessionId,
        studentId: PersonId,
    ): Either<PlanificacionError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.PLAN, Action.PERSONALIZE)) {
                denegado(actor, aggregateId = actor.userId, motivo = "RBAC")
                PlanificacionError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val plan = repository.findById(clubId, planId)
            ensureNotNull(plan) {
                denegado(actor, aggregateId = planId.value, motivo = "PlanNotFound")
                PlanificacionError.Forbidden
            }
            val coach = PersonId.of(actor.userId)
            ensure(coachGroupLookup.isCoachOfGroup(clubId, coach, plan.groupId)) {
                denegado(actor, aggregateId = planId.value, motivo = "NotCoachOfGroup", sujetoId = plan.groupId.value)
                PlanificacionError.Forbidden
            }

            plan.removePersonalization(sessionId, studentId).bind()
            repository.deletePersonalization(clubId, planId, sessionId, studentId)

            if (plan.status == PlanStatus.PUBLICADO) {
                val baseSession = plan.sessions.first { it.id == sessionId }
                eventPublisher.publishEvent(
                    PersonalizacionRetirada(
                        eventId = UuidCreator.getTimeOrderedEpoch(),
                        aggregateId = planId.value,
                        occurredAt = Instant.now(),
                        clubId = clubId.value,
                        actorId = actor.userId,
                        traceparent = OpenTelemetryHelper.actualTraceparent(),
                        grupoId = plan.groupId.value,
                        sesionId = sessionId.value,
                        dia = baseSession.day,
                        alumnoId = studentId.value,
                        baseSession = baseSession.toPersonalizedSession(),
                    ),
                )
            }
        }

    /** Publica [AccesoDenegado] (ADR-0009 D16) en la misma transacción que el rechazo — ver `PublishPlanCommand`. */
    private fun denegado(
        actor: Principal,
        aggregateId: UUID,
        motivo: String,
        sujetoId: UUID? = null,
    ) {
        eventPublisher.publishEvent(
            AccesoDenegado(
                eventId = UuidCreator.getTimeOrderedEpoch(),
                aggregateId = aggregateId,
                occurredAt = Instant.now(),
                clubId = actor.clubId,
                actorId = actor.userId,
                traceparent = OpenTelemetryHelper.actualTraceparent(),
                recurso = RECURSO_PERSONALIZAR,
                motivo = motivo,
                sujetoId = sujetoId,
            ),
        )
    }
}
