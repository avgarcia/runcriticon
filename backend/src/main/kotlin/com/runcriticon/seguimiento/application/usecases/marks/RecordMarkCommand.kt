package com.runcriticon.seguimiento.application.usecases.marks

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.api.events.MarcaActualizada
import com.runcriticon.seguimiento.application.SeguimientoAccessAuditor
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ConsentReader
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
 * Registro (o edición) de la propia marca del alumno en una distancia: envío idempotente, una marca
 * por distancia, sin histórico — la segunda llamada sobreescribe, no crea una fila nueva. Mismo criterio que
 * `SubmitSessionReportCommand`.
 *
 * **Orden de guardas**, mismo criterio que `SubmitSessionReportCommand`/`RescheduleDayCommand`: RBAC →
 * consentimiento vigente de datos de salud (ADR-0014 D18) → `StudentId.of(actor.userId)` (anti-IDOR:
 * `alumnoId` nunca es un parámetro) → invariante de dominio → persistencia → evento `MarcaActualizada`.
 *
 * **Sí requiere consentimiento**: la marca es dato de salud de categoría 1 (ADR-0014 D5, art. 9 RGPD) —
 * la revisión de LAL-140 corrigió este caso de uso, que antes omitía el gate por una lectura incorrecta de
 * D18 (el ADR no acota el rechazo tras revocar a `reporte_sesion`; cubre todo el módulo).
 *
 * **Sin `AccesoADatosSensibles`**: el alumno operando su propio dato queda excluido por
 * `rgpd-en-modulos.md` §5, mismo criterio que `GetMyWeekQuery`/`SubmitSessionReportCommand`.
 */
@ApplicationService
class RecordMarkCommand(
    private val repository: StudentMarkRepository,
    private val consentReader: ConsentReader,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
    private val auditor: SeguimientoAccessAuditor,
) {
    @Transactional
    fun execute(
        actor: Principal,
        distance: RaceDistance,
        timeSeconds: Int,
    ): Either<SeguimientoError, StudentMark> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.MARCA, Action.RECORD)) {
                auditor.rbacDenied(actor, Resource.MARCA, Action.RECORD)
                SeguimientoError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val studentId = StudentId.of(actor.userId)

            ensure(consentReader.isGranted(clubId, studentId)) {
                SeguimientoError.ConsentNotGranted
            }

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
                    distancia = distance.literal,
                    tiempoSegundos = timeSeconds,
                ),
            )

            mark
        }
}
