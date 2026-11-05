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
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
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
 * Baja que decide el admin (ADR-0014 D14): la cuenta pasa a `BAJA`, sus sesiones se revocan al instante y, durante los
 * [User.DELETION_GRACE] de gracia, no puede entrar por ninguna vía —todas las vías de autenticación exigen `ACTIVO`—.
 * Vencida la gracia, la suprime el job diario de `identidad` (ADR-0017 D9) salvo que el admin la cancele antes con
 * [CancelUserDeletionCommand].
 *
 * Fuera de `identidad` no cambia nada hasta la supresión: la persona sigue en sus grupos y planes, y no se publica
 * ningún evento. Por eso exige la misma acción que la supresión inmediata (`USER:DELETE`): es la supresión, con plazo.
 *
 * Mismas guardas que [DeleteUserCommand]: el admin no puede darse de baja a sí mismo ni dar de baja al último
 * administrador capaz de entrar. Marcar una cuenta que ya está en baja devuelve `Conflict` (409) en lugar de reiniciar
 * la gracia. El asiento [AuditEventType.BAJA_MARCADA] guarda quién pidió la baja: la supresión posterior ocurre sin
 * actor.
 */
@ApplicationService
class MarkUserDeletionCommand(
    private val userRepository: UserRepository,
    private val sessionRevoker: SessionRevoker,
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
            ensure(actor.userId != targetUserId.value) {
                IdentidadError.Conflict("no puedes darte de baja a ti mismo")
            }

            val target = userRepository.findByIdForUpdate(ClubId.of(actor.clubId), targetUserId)
            ensureNotNull(target) { IdentidadError.NotFound }
            ensure(target.status != UserStatus.BAJA) {
                IdentidadError.Conflict("la cuenta ya está en baja")
            }
            ensure(!userRepository.isLastAdminAbleToEnter(target)) {
                IdentidadError.Conflict("no puedes dar de baja al último administrador del club")
            }

            val now = Instant.now()
            userRepository.save(target.markForDeletion(now))
            sessionRevoker.revokeAll(target.id.value)
            auditTrail.record(
                AuditEntry(
                    type = AuditEventType.BAJA_MARCADA,
                    actorId = actor.userId,
                    subjectId = target.id.value,
                    occurredAt = now,
                ),
            )
        }
}
