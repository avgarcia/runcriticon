package com.runcriticon.seguimiento.application.usecases.adjustment

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.seguimiento.application.ports.outbound.persistence.DayAdjustmentRepository
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanReader
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Deshacer el reajuste de una sesión (LAL-33, el "deshacer" del toast): idempotente, igual que
 * `WithdrawMarkCommand` — `204` con o sin reajuste previo, nunca falla por "no encontrado".
 *
 * Borra por `operationId`, no por día: un `REEMPLAZAR`/`INTERCAMBIAR` escribió dos filas que comparten
 * `operationId`, y deshacer solo una dejaría la operación a medias — una sesión movida sin que la otra
 * vuelva a su sitio.
 */
@ApplicationService
class WithdrawDayAdjustmentCommand(
    private val reader: ResolvedPlanReader,
    private val repository: DayAdjustmentRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        day: LocalDate,
    ): Either<SeguimientoError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.DAY_ADJUSTMENT, Action.WITHDRAW)) {
                SeguimientoError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val studentId = StudentId.of(actor.userId)

            val session = reader.findDay(clubId, studentId, day)
            val operationId = session?.adjustment?.operationId ?: return@either

            repository.deleteByOperation(clubId, studentId, operationId)
            Unit
        }
}
