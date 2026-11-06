package com.runcriticon.identidad.application.usecases.account

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.IdentidadAccessAuditor
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Cancela la baja de [MarkUserDeletionCommand] mientras corre la gracia: la cuenta vuelve al estado que tenía al
 * marcarla. Exige `USER:DELETE`: quien puede decidir la supresión puede deshacerla mientras aún no se ha ejecutado. Las
 * sesiones revocadas al marcar la baja no vuelven; la persona inicia sesión de nuevo.
 *
 * Una cuenta que no está en baja devuelve `Conflict` (409), igual que una cuya gracia ya venció: está pendiente de que
 * el job diario la suprima (ADR-0017 D9) y la baja ya no se puede deshacer. Lee la cuenta bloqueando su fila para no
 * pisarse con ese job: si la está suprimiendo a la vez, espera y la encuentra ya borrada (404). Sin ese bloqueo,
 * guardar la cuenta después de que el job la borrara la volvería a insertar sin el resto de sus datos.
 */
@ApplicationService
class CancelUserDeletionCommand(
    private val userRepository: UserRepository,
    private val auditTrail: AuditTrail,
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

            val target = userRepository.findByIdForUpdate(ClubId.of(actor.clubId), targetUserId)
            ensureNotNull(target) { IdentidadError.NotFound }
            ensure(target.status == UserStatus.BAJA) {
                IdentidadError.Conflict("la cuenta no está en baja")
            }
            val now = Instant.now()
            ensure(!target.isDeletionGraceExpired(now)) {
                IdentidadError.Conflict("la gracia de la baja ya venció")
            }

            userRepository.save(target.cancelDeletion())
            auditTrail.record(
                AuditEntry(
                    type = AuditEventType.BAJA_CANCELADA,
                    actorId = UserId.of(actor.userId),
                    subjectId = target.id,
                    occurredAt = now,
                ),
            )
        }
}
