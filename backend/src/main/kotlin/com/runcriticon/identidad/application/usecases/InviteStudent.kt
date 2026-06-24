package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.api.events.AlumnoInvitado
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
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.observability.OpenTelemetryHelper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Alta de un alumno por invitación (CA principal de LAL-8, ADR-0003 D3). Lo ejecuta un ADMIN o un
 * ENTRENADOR — la delegación a entrenadores reparte la carga de registro (ADR-0009). Crea el usuario
 * en estado `INVITADO` con rol `ALUMNO`, emite la invitación de un solo uso, dispara el email vía
 * outbox, publica el integration event [AlumnoInvitado] para otros módulos y deja asiento de
 * auditoría — todo en una transacción.
 *
 * `@Transactional` es necesario para el outbox de Spring Modulith: los eventos se persisten en
 * `event_publication` dentro de la misma transacción que las escrituras de negocio, y se entregan
 * tras el commit. [AlumnoInvitado] hoy no tiene consumidor (el módulo Club aún no existe); se
 * multicast y queda disponible para cuando lo haya.
 */
@ApplicationService
class InviteStudent(
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
        name: String,
        emailRaw: String,
    ): Either<IdentidadError, UserId> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.INVITE)) {
                IdentidadError.Forbidden
            }
            ensure(name.isNotBlank()) { IdentidadError.InvalidInput("name", "required") }
            ensure(emailRaw.contains('@')) { IdentidadError.InvalidInput("email", "invalid") }

            val email = Email.of(emailRaw)
            ensure(userRepository.findByEmail(actor.clubId, email) == null) {
                IdentidadError.Conflict("ya existe un usuario con ese email en el club")
            }

            val now = Instant.now()
            val user = User.newInvited(actor.clubId, email, name.trim(), Role.ALUMNO)
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

            eventPublisher.publishEvent(
                AlumnoInvitado(
                    eventId = UUID.randomUUID(),
                    aggregateId = user.id.value,
                    occurredAt = now,
                    clubId = actor.clubId,
                    actorId = actor.userId,
                    traceparent = OpenTelemetryHelper.actualTraceparent(),
                    name = user.name,
                    email = user.email.value,
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
