package com.runcriticon.planificacion.application.usecases.plans

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.planificacion.application.ports.outbound.ProjectionFreshness
import com.runcriticon.planificacion.application.ports.outbound.persistence.CoachGroupLookup
import com.runcriticon.planificacion.application.ports.outbound.persistence.GroupMembersProjection
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.Pace
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.RaceDistance
import com.runcriticon.planificacion.domain.Session
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

private const val STALE_THRESHOLD_SECONDS = 60L

/**
 * Publica el plan semanal [planId] a su grupo (LAL-25): congela el snapshot de alumnos resuelto en este momento
 * (ADR-0002 D5) y emite [PlanPublicado], auto-contenido (ADR-0007 D15).
 *
 * **Orden de las guardas**, mismo criterio que `AddSessionCommand`: RBAC → carga del plan → relación con el
 * grupo (AC3) → frescura de la proyección de membresía (ADR-0009 D9) → invariantes de dominio → persistencia →
 * evento. La puerta de frescura va **después** de la autorización y **antes** de resolver el snapshot: denegar
 * por proyección atrasada no debe revelar nada a quien ni siquiera es entrenador del grupo.
 */
@ApplicationService
class PublishPlanCommand(
    private val repository: WeeklyPlanRepository,
    private val coachGroupLookup: CoachGroupLookup,
    private val groupMembers: GroupMembersProjection,
    private val freshness: ProjectionFreshness,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /** [studentsInSnapshot] va aparte del agregado: `WeeklyPlan` no conoce su propia membresía, solo el caso de uso. */
    data class Result(
        val plan: WeeklyPlan,
        val studentsInSnapshot: Int,
    )

    @Transactional
    fun execute(
        actor: Principal,
        planId: PlanId,
    ): Either<PlanificacionError, Result> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.PLAN, Action.PUBLISH)) {
                PlanificacionError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val plan = repository.findById(clubId, planId)
            ensureNotNull(plan) { PlanificacionError.Forbidden }
            val coach = PersonId.of(actor.userId)
            ensure(coachGroupLookup.isCoachOfGroup(clubId, coach, plan.groupId)) { PlanificacionError.Forbidden }

            val lag = freshness.membersProjectionLagSeconds()
            ensure(lag < STALE_THRESHOLD_SECONDS) { PlanificacionError.ProjectionStale(lag) }

            val published = plan.publish().bind()
            val snapshot = groupMembers.findStudents(clubId, plan.groupId)
            repository.publish(clubId, planId, snapshot)

            eventPublisher.publishEvent(
                PlanPublicado(
                    eventId = UuidCreator.getTimeOrderedEpoch(),
                    aggregateId = planId.value,
                    occurredAt = Instant.now(),
                    clubId = clubId.value,
                    actorId = actor.userId,
                    traceparent = OpenTelemetryHelper.actualTraceparent(),
                    grupoId = plan.groupId.value,
                    snapshotAlumnos = snapshot.map(PersonId::value),
                    sesiones = published.sessions.map { it.toPublishedSession() },
                ),
            )

            Result(plan = published, studentsInSnapshot = snapshot.size)
        }
}

private fun Session.toPublishedSession(): PublishedSession {
    val volume = volume
    val pace = pace
    return PublishedSession(
        dia = day,
        tipo = type.name,
        volumenTipo =
            when (volume) {
                null -> null
                is SessionVolume.Distance -> "DISTANCIA"
                is SessionVolume.Duration -> "TIEMPO"
            },
        volumenMetros = (volume as? SessionVolume.Distance)?.meters,
        volumenMinutos = (volume as? SessionVolume.Duration)?.minutes,
        ritmoTipo =
            when (pace) {
                null -> null
                is Pace.Absoluto -> "ABSOLUTO"
                is Pace.Relativo -> "RELATIVO"
            },
        ritmoSegundosPorKm = (pace as? Pace.Absoluto)?.secondsPerKm,
        ritmoReferencia = (pace as? Pace.Relativo)?.reference?.toReferenciaLiteral(),
        ritmoDeltaSegundosPorKm = (pace as? Pace.Relativo)?.deltaSecondsPerKm,
        notas = notes,
    )
}

private fun RaceDistance.toReferenciaLiteral(): String =
    when (this) {
        RaceDistance.FIVE_K -> "5K"
        RaceDistance.TEN_K -> "10K"
        RaceDistance.HALF_MARATHON -> "21K"
        RaceDistance.MARATHON -> "42K"
    }
