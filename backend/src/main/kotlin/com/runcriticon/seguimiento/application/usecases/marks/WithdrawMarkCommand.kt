package com.runcriticon.seguimiento.application.usecases.marks

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.api.events.MarcaRetirada
import com.runcriticon.seguimiento.application.ports.outbound.persistence.StudentMarkRepository
import com.runcriticon.seguimiento.domain.RaceDistance
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

/**
 * Retirada de la propia marca del alumno en una distancia (LAL-31): idempotente — si no había marca, no
 * falla y no emite nada. Solo se publica `MarcaRetirada` cuando de verdad borra una fila, para que el
 * consumidor futuro (LAL-32) tenga la garantía de que todo evento recibido corresponde a un cambio real.
 */
@ApplicationService
class WithdrawMarkCommand(
    private val repository: StudentMarkRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    @Transactional
    fun execute(
        actor: Principal,
        distance: RaceDistance,
    ): Either<SeguimientoError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.MARCA, Action.WITHDRAW)) {
                SeguimientoError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val studentId = StudentId.of(actor.userId)

            val deleted = repository.delete(clubId, studentId, distance)
            if (!deleted) return@either

            eventPublisher.publishEvent(
                MarcaRetirada(
                    eventId = UuidCreator.getTimeOrderedEpoch(),
                    aggregateId = actor.userId,
                    occurredAt = Instant.now(clock),
                    clubId = actor.clubId,
                    actorId = actor.userId,
                    traceparent = OpenTelemetryHelper.actualTraceparent(),
                    distancia = distance.toLiteral(),
                ),
            )
        }
}

private fun RaceDistance.toLiteral(): String =
    when (this) {
        RaceDistance.FIVE_K -> "5K"
        RaceDistance.TEN_K -> "10K"
        RaceDistance.HALF_MARATHON -> "21K"
        RaceDistance.MARATHON -> "42K"
    }
