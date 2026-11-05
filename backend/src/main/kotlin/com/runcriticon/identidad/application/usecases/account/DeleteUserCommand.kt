package com.runcriticon.identidad.application.usecases.account

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.IdentidadAccessAuditor
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.SessionRevoker
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Supresión **inmediata** de una persona del club: revoca sus sesiones y la suprime con [UserEraser]. Es el camino de
 * la supresión que pide el titular, que no tiene gracia para cumplir el plazo de un mes (ADR-0014 D11 y D14); la que
 * decide el admin pasa antes por [MarkUserDeletionCommand]. Solo el ADMIN. Es **irreversible**, a diferencia de
 * [DeactivateUserCommand], que solo cambia el estado de la cuenta.
 *
 * **Sin guarda de estado**: se puede eliminar una cuenta `INVITADO`, `ACTIVO`, `DESACTIVADO` o en `BAJA`. El derecho
 * de supresión no depende del estado de la cuenta, así que exigirlo dejaría sin atender a quien pidiera el borrado de
 * una invitación que nunca llegó a aceptar, o a quien lo pidiera mientras corre la gracia de su baja.
 *
 * Lee la cuenta bloqueando su fila: si el job de las bajas vencidas la está suprimiendo a la vez, espera a que termine
 * y la encuentra ya borrada (404), en vez de suprimirla dos veces.
 *
 * Todo ocurre en una transacción, incluida la publicación al outbox: si el borrado hace rollback, el evento no sale.
 */
@ApplicationService
class DeleteUserCommand(
    private val userRepository: UserRepository,
    private val userEraser: UserEraser,
    private val sessionRevoker: SessionRevoker,
    private val auditor: IdentidadAccessAuditor,
) {
    @Transactional
    fun execute(
        actor: Principal,
        targetUserId: UserId,
    ): Either<IdentidadError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.USER, Action.DELETE)) {
                auditor.denegado(actor, Resource.USER, Action.DELETE)
                IdentidadError.Forbidden
            }
            ensure(actor.userId != targetUserId.value) {
                IdentidadError.Conflict("no puedes eliminar tu propia cuenta")
            }

            val clubId = ClubId.of(actor.clubId)
            val target = userRepository.findByIdForUpdate(clubId, targetUserId)
            ensureNotNull(target) { IdentidadError.NotFound }
            ensure(!userRepository.isLastAdminAbleToEnter(target)) {
                IdentidadError.Conflict("no puedes eliminar el último administrador del club")
            }

            sessionRevoker.revokeAll(target.id.value)
            // El actor es el admin que ejecuta la supresión, no la persona suprimida: es quien provoca el hecho.
            userEraser.erase(target, actorId = actor.userId, now = Instant.now())
        }
}
