package com.runcriticon.identidad.application
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.application.ports.inbound.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.ports.outbound.security.TokenGenerator
import com.runcriticon.identidad.application.ports.outbound.security.TokenHasher
import com.runcriticon.identidad.application.ratelimit.RateLimitMetrics
import com.runcriticon.identidad.application.ratelimit.RateLimiter
import com.runcriticon.identidad.application.ratelimit.consumeForActor
import com.runcriticon.identidad.application.usecases.invitation.InviteCoachCommand
import com.runcriticon.identidad.application.usecases.invitation.InviteStudentCommand
import com.runcriticon.identidad.application.usecases.invitation.ResendInvitationCommand
import com.runcriticon.identidad.application.usecases.invitation.ResendStudentInvitationCommand
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.events.UserInvited
import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Orquestación compartida de alta y reenvío de invitaciones (LAL-62): rate-limit por actor, token de
 * un solo uso, email vía outbox y auditoría. Colaborador interno (`@Component`, no `@ApplicationService`,
 * mismo molde que [PasswordPolicy]) inyectado por [InviteCoachCommand], [InviteStudentCommand], [ResendInvitationCommand] y
 * [ResendStudentInvitationCommand], que quedan como cascarones finos: solo hacen su check de
 * `AuthorizationMatrix` con su propio `Resource` y delegan aquí.
 *
 * `@Transactional(MANDATORY)`: hoy no cambia nada (los 4 llamadores ya abren transacción para que el
 * outbox de Spring Modulith persista `event_publication` en la misma transacción, ADR-0007), pero
 * convierte un futuro caso de uso que olvide su propio `@Transactional` en un fallo inmediato en vez
 * de una escritura no-atómica silenciosa.
 */
@Component
class InvitationIssuer(
    private val userRepository: UserRepository,
    private val invitationRepository: InvitationRepository,
    private val tokenGenerator: TokenGenerator,
    private val tokenHasher: TokenHasher,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
    private val rateLimiter: RateLimiter,
    private val rateLimitMetrics: RateLimitMetrics,
) {
    /**
     * Alta: crea el usuario `INVITADO` con [role] y emite su primera invitación. Devuelve el domain
     * event [UserInvited] (ADR-0008 D2/D4): el llamador decide si lo traduce a un integration event
     * público según el rol — hoy solo [com.runcriticon.identidad.application.usecases.invitation.InviteStudentCommand] lo hace.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun issue(
        actor: Principal,
        name: String,
        emailRaw: String,
        role: Role,
    ): Either<IdentidadError, UserInvited> =
        either {
            consumeForActor(rateLimiter, rateLimitMetrics, auditTrail, actor.userId)
            ensure(name.isNotBlank()) { IdentidadError.InvalidInput("name", "required") }
            ensure(emailRaw.contains('@')) { IdentidadError.InvalidInput("email", "invalid") }

            val email = Email.of(emailRaw)
            val clubId = ClubId.of(actor.clubId)
            ensure(userRepository.findByEmail(clubId, email) == null) {
                IdentidadError.Conflict("ya existe un usuario con ese email en el club")
            }

            val now = Instant.now()
            val user = User.newInvited(clubId, email, name.trim(), role)
            userRepository.save(user)

            val rawToken = tokenGenerator.generate()
            val invitation = Invitation.issue(user.id, clubId, tokenHasher.hash(rawToken), now)
            invitationRepository.save(invitation)

            notifyAndAudit(actor, user, rawToken, invitation.expiresAt, now)
            UserInvited(
                eventId = UuidCreator.getTimeOrderedEpoch(),
                occurredAt = now,
                user = user,
                actorId = actor.userId,
            )
        }

    /**
     * Reinvitación: exige que [userId] exista, sea de [expectedRole] y siga `INVITADO`; rota el token
     * (ADR-0003 D4). El check de rol es simétrico para entrenador y alumno — cierra LAL-62.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun reissueFor(
        actor: Principal,
        userId: UserId,
        expectedRole: Role,
    ): Either<IdentidadError, User> =
        either {
            consumeForActor(rateLimiter, rateLimitMetrics, auditTrail, actor.userId)

            val user = userRepository.findById(ClubId.of(actor.clubId), userId)
            ensureNotNull(user) { IdentidadError.NotFound }
            ensure(user.role == expectedRole) { IdentidadError.NotFound }
            ensure(user.status == UserStatus.INVITADO) {
                IdentidadError.Conflict("el usuario no está pendiente de activar")
            }

            val current = invitationRepository.findLatestByUserId(userId)
            ensureNotNull(current) { IdentidadError.Conflict("no hay invitación previa") }

            val now = Instant.now()
            val rawToken = tokenGenerator.generate()
            val (invalidated, fresh) = current.reissue(tokenHasher.hash(rawToken), now)
            invitationRepository.save(invalidated)
            invitationRepository.save(fresh)

            notifyAndAudit(actor, user, rawToken, fresh.expiresAt, now)
            user
        }

    /** Publica el email de invitación vía outbox y deja el asiento de auditoría `INVITACION_EMITIDA`. */
    private fun notifyAndAudit(
        actor: Principal,
        user: User,
        rawToken: RawToken,
        expiresAt: Instant,
        now: Instant,
    ) {
        eventPublisher.publishEvent(
            InvitationEmailRequested(
                to = user.email,
                recipientName = user.name,
                rawToken = rawToken,
                expiresAt = expiresAt,
                clubId = actor.clubId,
                actorId = actor.userId,
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
