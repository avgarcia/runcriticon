package com.runcriticon.identidad.application.usecases.session

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
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
 * Revocación por admin de todas las sesiones activas de un usuario. Solo el ADMIN puede ejecutarlo. Comprueba que el
 * usuario objetivo existe **en el club del principal** ([UserRepository.findById] filtra por club: un id de otro club
 * devuelve `NotFound`) y borra todas sus filas de Spring Session ([SessionRevoker]). Deja asiento de auditoría
 * [AuditEventType.SESION_REVOCADA] con `actorId = admin` y `subjectId = target`.
 */
@ApplicationService
class RevokeUserSessionsCommand(
    private val userRepository: UserRepository,
    private val sessionRevoker: SessionRevoker,
    private val auditTrail: AuditTrail,
) {
    @Transactional
    fun execute(
        actor: Principal,
        targetUserId: UserId,
    ): Either<IdentidadError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.USER, Action.REVOKE_SESSIONS)) {
                IdentidadError.Forbidden
            }

            val user = userRepository.findById(ClubId.of(actor.clubId), targetUserId)
            ensureNotNull(user) { IdentidadError.NotFound }

            sessionRevoker.revokeAll(user.id.value)

            auditTrail.record(
                AuditEntry(
                    type = AuditEventType.SESION_REVOCADA,
                    actorId = actor.userId,
                    subjectId = user.id.value,
                    occurredAt = Instant.now(),
                ),
            )
        }
}
