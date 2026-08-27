package com.runcriticon.planificacion.application.usecases.personalizations

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.auditoria.api.events.AccesoDenegado
import com.runcriticon.planificacion.api.events.PersonalizacionAplicada
import com.runcriticon.planificacion.application.ports.outbound.persistence.CoachGroupLookup
import com.runcriticon.planificacion.application.ports.outbound.persistence.GroupMembersProjection
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.Pace
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.Personalization
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanStatus
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.planificacion.domain.SessionOverride
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.SessionVolume
import com.runcriticon.planificacion.domain.WeeklyPlan
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
 * Aplica o sustituye la personalización de un alumno en una sesión (LAL-26). Upsert idempotente: repetir la
 * misma llamada deja el mismo estado (mismo criterio que `ajustarPertenenciaAGrupo` de `club_taxonomia`).
 *
 * **Orden de guardas**, mismo criterio que `PublishPlanCommand`: RBAC → carga del plan → relación con el
 * grupo → pertenencia del alumno al plan (contra el grupo si `BORRADOR`, contra el snapshot congelado si
 * `PUBLICADO` — AC2/AC3) → invariantes de dominio → persistencia → evento **solo si el plan ya está
 * `PUBLICADO`** (AC2: antes de publicar no hay snapshot al que proyectar Seguimiento; la personalización
 * viaja igualmente al publicar, dentro de `PlanPublicado.personalizaciones`).
 *
 * Sin puerta `ProjectionFreshness`: solo la lleva `PublishPlanCommand`, porque congelar un snapshot
 * desactualizado es irreversible. Personalizar contra una membresía con segundos de retraso es recuperable.
 *
 * Devuelve el [WeeklyPlan] completo recalculado, no solo la [Personalization] — mismo criterio que
 * `OverrideGroupMembershipCommand` de `club_taxonomia`: evita que el cliente tenga que hacer una segunda
 * consulta para ver el detalle actualizado del plan.
 */
@ApplicationService
class SetPersonalizationCommand(
    private val repository: WeeklyPlanRepository,
    private val coachGroupLookup: CoachGroupLookup,
    private val groupMembers: GroupMembersProjection,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Suppress("LongParameterList")
    @Transactional
    fun execute(
        actor: Principal,
        planId: PlanId,
        sessionId: SessionId,
        studentId: PersonId,
        type: SessionType,
        volume: SessionVolume?,
        pace: Pace?,
        notes: String?,
        messageToStudent: String?,
    ): Either<PlanificacionError, WeeklyPlan> =
        either {
            val (clubId, plan) = loadAuthorizedPlan(actor, planId)
            ensure(studentBelongsToPlan(clubId, planId, plan, studentId)) { PlanificacionError.StudentNotInPlan }

            val override = SessionOverride.create(type, volume, pace, notes).bind()
            val personalization =
                Personalization
                    .create(
                        sessionId = sessionId,
                        studentId = studentId,
                        override = override,
                        messageToStudent = messageToStudent,
                    ).bind()
            val updated = plan.setPersonalization(personalization).bind()
            repository.upsertPersonalization(clubId, planId, personalization)

            if (plan.status == PlanStatus.PUBLICADO) {
                val event =
                    personalizacionAplicadaEvent(
                        actor = actor,
                        planId = planId,
                        plan = plan,
                        updated = updated,
                        sessionId = sessionId,
                        studentId = studentId,
                        override = override,
                        messageToStudent = messageToStudent,
                    )
                eventPublisher.publishEvent(event)
            }

            updated
        }

    /** RBAC → plan cargado → relación vigente con el grupo. Extraído para mantener [execute] dentro del tope
     * de `detekt` — mismo bloque de guardas que `PublishPlanCommand`, ver su KDoc. */
    private fun Raise<PlanificacionError>.loadAuthorizedPlan(
        actor: Principal,
        planId: PlanId,
    ): Pair<ClubId, WeeklyPlan> {
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
        return clubId to plan
    }

    /** Grupo si `BORRADOR` (AC2), snapshot congelado si `PUBLICADO` (AC3) — ver KDoc de la clase. */
    private fun studentBelongsToPlan(
        clubId: ClubId,
        planId: PlanId,
        plan: WeeklyPlan,
        studentId: PersonId,
    ): Boolean =
        if (plan.status == PlanStatus.PUBLICADO) {
            repository.isStudentInSnapshot(clubId, planId, studentId)
        } else {
            groupMembers.findStudents(clubId, plan.groupId).contains(studentId)
        }

    /** Ensambla [PersonalizacionAplicada]. Extraído aparte para mantener [execute] dentro del tope de `detekt`. */
    @Suppress("LongParameterList")
    private fun personalizacionAplicadaEvent(
        actor: Principal,
        planId: PlanId,
        plan: WeeklyPlan,
        updated: WeeklyPlan,
        sessionId: SessionId,
        studentId: PersonId,
        override: SessionOverride,
        messageToStudent: String?,
    ) = PersonalizacionAplicada(
        eventId = UuidCreator.getTimeOrderedEpoch(),
        aggregateId = planId.value,
        occurredAt = Instant.now(),
        clubId = plan.clubId.value,
        actorId = actor.userId,
        traceparent = OpenTelemetryHelper.actualTraceparent(),
        grupoId = plan.groupId.value,
        sesionId = sessionId.value,
        dia = updated.sessions.first { it.id == sessionId }.day,
        alumnoId = studentId.value,
        override = override.toPersonalizedSession(),
        mensajeAlAlumno = messageToStudent,
    )

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
