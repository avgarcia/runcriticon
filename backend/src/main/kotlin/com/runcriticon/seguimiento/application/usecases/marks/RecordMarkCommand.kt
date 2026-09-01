package com.runcriticon.seguimiento.application.usecases.marks

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.api.events.MarcaActualizada
import com.runcriticon.seguimiento.application.ports.outbound.persistence.StudentMarkRepository
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
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
 * Registro (o edición) de la propia marca del alumno en una distancia (LAL-31): envío idempotente, una marca
 * por distancia, sin histórico — la segunda llamada sobreescribe, no crea una fila nueva. Mismo criterio que
 * `SubmitSessionReportCommand`.
 *
 * **Orden de guardas**: RBAC → `StudentId.of(actor.userId)` (anti-IDOR: `alumnoId` nunca es un parámetro) →
 * invariante de dominio → persistencia → evento `MarcaActualizada`.
 *
 * **Sin consultar consentimiento**: a diferencia de `SubmitSessionReportCommand`, la marca no es un dato de
 * sesión ejecutada cubierto por el consentimiento de datos de salud (ADR-0014 D18 lo ata a `reporte_sesion`);
 * es un tiempo de referencia que el alumno introduce voluntariamente para calcular sus ritmos.
 *
 * **Sin `AccesoADatosSensibles`**: el alumno operando su propio dato queda excluido por
 * `rgpd-en-modulos.md` §5, mismo criterio que `GetMyWeekQuery`/`SubmitSessionReportCommand`.
 */
@ApplicationService
class RecordMarkCommand(
    private val repository: StudentMarkRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    @Transactional
    fun execute(
        actor: Principal,
        distance: RaceDistance,
        timeSeconds: Int,
    ): Either<SeguimientoError, StudentMark> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.MARCA, Action.RECORD)) {
                SeguimientoError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val studentId = StudentId.of(actor.userId)

            val now = Instant.now(clock)
            val mark = StudentMark.create(distance, timeSeconds, now).bind()

            repository.upsert(clubId, studentId, mark)

            eventPublisher.publishEvent(
                MarcaActualizada(
                    eventId = UuidCreator.getTimeOrderedEpoch(),
                    aggregateId = actor.userId,
                    occurredAt = now,
                    clubId = actor.clubId,
                    actorId = actor.userId,
                    traceparent = OpenTelemetryHelper.actualTraceparent(),
                    distancia = distance.toLiteral(),
                    tiempoSegundos = timeSeconds,
                ),
            )

            mark
        }
}

// A nivel de fichero, no en dominio: el puente a los literales del evento/contrato vive en el mapeador de
// infraestructura/aplicación, mismo criterio que `ResolvedPlanProjectionJdbc.toLiteral()`.
private fun RaceDistance.toLiteral(): String =
    when (this) {
        RaceDistance.FIVE_K -> "5K"
        RaceDistance.TEN_K -> "10K"
        RaceDistance.HALF_MARATHON -> "21K"
        RaceDistance.MARATHON -> "42K"
    }
