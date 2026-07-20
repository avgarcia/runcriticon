package com.runcriticon.identidad.application.usecases.account

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
 * Desactivación de una cuenta por admin. Solo el ADMIN. Comprueba que el usuario objetivo existe en el club del
 * principal ([UserRepository.findById] filtra por club: otro club → `NotFound`), lo pasa a `DESACTIVADO`, revoca sus
 * sesiones activas ([SessionRevoker]: logout inmediato) y deja asiento [AuditEventType.CUENTA_DESACTIVADA]— todo en una
 * transacción.
 *
 * Reintentar sobre una cuenta ya `DESACTIVADO` devuelve `Conflict` (409): el guard de estado se comprueba aquí, en la
 * aplicación, para no dejar que el invariante del dominio ([User.deactivate] usa `require`) escale como excepción de
 * framework.
 *
 * El *gate-check* que rechaza peticiones de una cuenta desactivada cuya sesión sobreviva vive en el
 * filtro [com.runcriticon.shared.autorizacion.spring.AccountStatusFilter] (defensa en profundidad).
 */
@ApplicationService
class DeactivateUserCommand(
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
            ensure(AuthorizationMatrix.can(actor.role, Resource.USER, Action.DEACTIVATE)) {
                IdentidadError.Forbidden
            }

            val target = userRepository.findById(ClubId.of(actor.clubId), targetUserId)
            ensureNotNull(target) { IdentidadError.NotFound }
            ensure(target.status == UserStatus.ACTIVO) {
                IdentidadError.Conflict("la cuenta no está activa")
            }

            val now = Instant.now()
            val deactivated = target.deactivate(now)
            userRepository.save(deactivated)

            sessionRevoker.revokeAll(deactivated.id.value)

            auditTrail.record(
                AuditEntry(
                    type = AuditEventType.CUENTA_DESACTIVADA,
                    actorId = actor.userId,
                    subjectId = deactivated.id.value,
                    occurredAt = now,
                ),
            )
        }
}
