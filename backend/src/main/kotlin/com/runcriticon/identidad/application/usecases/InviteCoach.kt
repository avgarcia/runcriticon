package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.InvitationRepository
import com.runcriticon.identidad.application.ports.TokenGenerator
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.application.ratelimit.RateLimitMetrics
import com.runcriticon.identidad.application.ratelimit.RateLimiter
import com.runcriticon.identidad.application.ratelimit.consumeForActor
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
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
 * Alta de un entrenador por invitación (ADR-0003 D3, CA principal de LAL-7). Solo el ADMIN puede
 * ejecutarlo (ADR-0009). Crea el usuario en estado `INVITADO`, emite la invitación de un solo uso,
 * dispara el email vía outbox y deja asiento de auditoría — todo en una transacción.
 *
 * `@Transactional` es necesario para el outbox de Spring Modulith: el [InvitationEmailRequested]
 * se persiste en `event_publication` dentro de la misma transacción que las escrituras de negocio,
 * y se entrega a [com.runcriticon.identidad.infrastructure.email.InvitationEmailListener] tras el commit.
 */
@ApplicationService
class InviteCoach(
    private val userRepository: UserRepository,
    private val invitationRepository: InvitationRepository,
    private val tokenGenerator: TokenGenerator,
    private val tokenHasher: TokenHasher,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
    private val rateLimiter: RateLimiter,
    private val rateLimitMetrics: RateLimitMetrics,
) {
    @Transactional
    fun execute(
        actor: Principal,
        name: String,
        emailRaw: String,
    ): Either<IdentidadError, UserId> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.COACH, Action.INVITE)) {
                IdentidadError.Forbidden
            }
            // Rate-limit por actor (100/h, ADR-0003 D12): tras autorizar, antes de crear al usuario.
            consumeForActor(rateLimiter, rateLimitMetrics, auditTrail, actor.userId)
            ensure(name.isNotBlank()) { IdentidadError.InvalidInput("name", "required") }
            ensure(emailRaw.contains('@')) { IdentidadError.InvalidInput("email", "invalid") }

            val email = Email.of(emailRaw)
            ensure(userRepository.findByEmail(actor.clubId, email) == null) {
                IdentidadError.Conflict("ya existe un usuario con ese email en el club")
            }

            val now = Instant.now()
            val user = User.newInvited(actor.clubId, email, name.trim(), Role.ENTRENADOR)
            userRepository.save(user)

            val rawToken = tokenGenerator.generate()
            val invitation = Invitation.issue(user.id, actor.clubId, tokenHasher.hash(rawToken), now)
            invitationRepository.save(invitation)

            eventPublisher.publishEvent(
                InvitationEmailRequested(
                    to = email,
                    recipientName = user.name,
                    rawToken = rawToken,
                    expiresAt = invitation.expiresAt,
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

            user.id
        }
}
