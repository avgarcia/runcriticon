package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.InvitationRepository
import com.runcriticon.identidad.application.ports.TokenGenerator
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Reinvitación de un alumno existente (LAL-8, paridad con la reinvitación de entrenador). La pueden
 * ejecutar admin y entrenador (delegación, ADR-0003 D3; ADR-0009). Invalida la invitación anterior y
 * emite un nuevo token de un solo uso (rotación, ADR-0003 D4) aunque la anterior no haya caducado,
 * reenvía el email vía outbox y deja asiento de auditoría — todo en una transacción.
 *
 * A diferencia de [InviteStudent], **no** publica `AlumnoInvitado`: la reinvitación no es un alta.
 *
 * `@Transactional` es necesario para el outbox de Spring Modulith: el [InvitationEmailRequested] se
 * persiste en `event_publication` dentro de la misma transacción que las escrituras de negocio.
 */
@ApplicationService
class ResendStudentInvitation(
    private val userRepository: UserRepository,
    private val invitationRepository: InvitationRepository,
    private val tokenGenerator: TokenGenerator,
    private val tokenHasher: TokenHasher,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun execute(
        actor: Principal,
        studentId: UserId,
    ): Either<IdentidadError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.INVITE)) {
                IdentidadError.Forbidden
            }

            val user = userRepository.findById(actor.clubId, studentId)
            ensureNotNull(user) { IdentidadError.NotFound }
            // El endpoint de alumnos solo reinvita alumnos: un id de entrenador se trata como no encontrado.
            ensure(user.role == Role.ALUMNO) { IdentidadError.NotFound }

            ensure(user.status == UserStatus.INVITADO) {
                IdentidadError.Conflict("el usuario no está pendiente de activar")
            }

            val current = invitationRepository.findLatestByUserId(studentId)
            ensureNotNull(current) { IdentidadError.Conflict("no hay invitación previa") }

            // TODO(LAL-35): rate-limit 100/h por actor (ADR-0003 D12)

            val now = Instant.now()
            val rawToken = tokenGenerator.generate()
            val (invalidated, fresh) = current.reissue(tokenHasher.hash(rawToken), now)
            invitationRepository.save(invalidated)
            invitationRepository.save(fresh)

            publishReissue(actor, user, rawToken, fresh, now)
        }

    /** Reenvía el email de invitación con el token nuevo y deja el asiento de auditoría. */
    private fun publishReissue(
        actor: Principal,
        user: User,
        rawToken: RawToken,
        fresh: Invitation,
        now: Instant,
    ) {
        eventPublisher.publishEvent(
            InvitationEmailRequested(
                to = user.email,
                recipientName = user.name,
                rawToken = rawToken,
                expiresAt = fresh.expiresAt,
            ),
        )

        auditTrail.record(
            AuditEntry(
                type = AuditEventType.INVITACION_EMITIDA,
                actorId = actor.userId,
                subjectId = user.id.value,
                occurredAt = now,
            ),
        )
    }
}
