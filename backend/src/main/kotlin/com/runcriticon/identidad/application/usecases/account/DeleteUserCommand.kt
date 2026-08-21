package com.runcriticon.identidad.application.usecases.account

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.MagicLinkRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.PasswordHistory
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
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.observability.OpenTelemetryHelper
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Supresión de una persona del club: borra físicamente al usuario y los datos personales que cuelgan de él, revoca sus
 * sesiones y publica la baja para que el resto de módulos borre sus proyecciones locales. Solo el ADMIN. Es
 * **irreversible**, a diferencia de [DeactivateUserCommand], que solo cambia el estado de la cuenta.
 *
 * **Sin guarda de estado**: se puede eliminar una cuenta `INVITADO`, `ACTIVO` o `DESACTIVADO`. El derecho de supresión
 * no depende de que la cuenta esté activa, así que exigirlo dejaría sin atender a quien pidiera el borrado de una
 * invitación que nunca llegó a aceptar.
 *
 * Todo ocurre en una transacción, incluida la publicación al outbox: si el borrado hace rollback, el evento no sale.
 *
 * Rastro que queda: el asiento [AuditEventType.CUENTA_ELIMINADA], que se registra ya sin `subjectId` — el enlace
 * entre esta baja y la persona suprimida vive en el runbook de atención de la solicitud, no en la tabla. Antes de
 * escribirlo, se anonimizan todos los asientos previos que mencionaban a la persona (como actor o como sujeto) y los
 * que solo quedaron ligados a su email por el `email_hash` de un evento de rate-limiting. El barrido va primero
 * porque `auditTrail.record` persiste vía JPA sin flush inmediato: anonimizar después no vería la fila recién
 * escrita.
 */
@ApplicationService
class DeleteUserCommand(
    private val userRepository: UserRepository,
    private val invitationRepository: InvitationRepository,
    private val magicLinkRepository: MagicLinkRepository,
    private val passwordHistory: PasswordHistory,
    private val sessionRevoker: SessionRevoker,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun execute(
        actor: Principal,
        targetUserId: UserId,
    ): Either<IdentidadError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.USER, Action.DELETE)) {
                IdentidadError.Forbidden
            }
            ensure(actor.userId != targetUserId.value) {
                IdentidadError.Conflict("no puedes eliminar tu propia cuenta")
            }

            val clubId = ClubId.of(actor.clubId)
            val target = userRepository.findById(clubId, targetUserId)
            ensureNotNull(target) { IdentidadError.NotFound }
            ensure(!isLastAdmin(clubId, target)) {
                IdentidadError.Conflict("no puedes eliminar el último administrador del club")
            }

            sessionRevoker.revokeAll(target.id.value)
            eraseIn(clubId, target.id)
            auditTrail.anonymize(target.id.value, target.email)

            val now = Instant.now()
            auditTrail.record(
                AuditEntry(
                    type = AuditEventType.CUENTA_ELIMINADA,
                    actorId = actor.userId,
                    subjectId = null,
                    occurredAt = now,
                ),
            )
            publishDeleted(target, actor, now)
        }

    /**
     * Un club sin ningún administrador capaz de entrar queda huérfano: nadie podría volver a invitar ni gestionar nada.
     * Cuenta los administradores que **no** están desactivados —un admin `INVITADO` cuenta, porque aún puede activar su
     * cuenta y recuperar el club; uno `DESACTIVADO` no, porque no puede iniciar sesión—; si el objetivo es el único
     * que queda, se rechaza.
     */
    private fun isLastAdmin(
        clubId: ClubId,
        target: User,
    ): Boolean =
        target.role == Role.ADMIN &&
            userRepository.countByRoleExcludingStatus(clubId, Role.ADMIN, UserStatus.DESACTIVADO) <= 1

    /**
     * Orden impuesto por las claves ajenas a `usuario`, que no tienen `ON DELETE CASCADE`: primero todo lo que apunta
     * al usuario, después el usuario.
     */
    private fun eraseIn(
        clubId: ClubId,
        userId: UserId,
    ) {
        magicLinkRepository.deleteByUserId(clubId, userId)
        invitationRepository.deleteByUserId(clubId, userId)
        passwordHistory.deleteByUserId(clubId, userId)
        userRepository.deleteById(clubId, userId)
    }

    /**
     * Publica la baja según el rol. El ADMIN no genera evento: no existe como persona proyectada en los módulos que
     * consumen estas bajas, igual que tampoco genera evento su alta.
     */
    private fun publishDeleted(
        deleted: User,
        actor: Principal,
        occurredAt: Instant,
    ) {
        val event: IntegrationEvent =
            when (deleted.role) {
                Role.ALUMNO -> alumnoEliminado(deleted, actor, occurredAt)
                Role.ENTRENADOR -> entrenadorEliminado(deleted, actor, occurredAt)
                Role.ADMIN -> return
            }
        eventPublisher.publishEvent(event)
    }

    // `actorId` es el admin que ejecuta la supresión, no la persona suprimida: es quien provoca el hecho. El sujeto
    // viaja en `aggregateId`, que es lo que el consumidor necesita para saber a quién borrar.
    private fun alumnoEliminado(
        deleted: User,
        actor: Principal,
        occurredAt: Instant,
    ) = AlumnoEliminado(
        eventId = UuidCreator.getTimeOrderedEpoch(),
        aggregateId = deleted.id.value,
        occurredAt = occurredAt,
        clubId = deleted.clubId.value,
        actorId = actor.userId,
        traceparent = OpenTelemetryHelper.actualTraceparent(),
    )

    private fun entrenadorEliminado(
        deleted: User,
        actor: Principal,
        occurredAt: Instant,
    ) = EntrenadorEliminado(
        eventId = UuidCreator.getTimeOrderedEpoch(),
        aggregateId = deleted.id.value,
        occurredAt = occurredAt,
        clubId = deleted.clubId.value,
        actorId = actor.userId,
        traceparent = OpenTelemetryHelper.actualTraceparent(),
    )
}
