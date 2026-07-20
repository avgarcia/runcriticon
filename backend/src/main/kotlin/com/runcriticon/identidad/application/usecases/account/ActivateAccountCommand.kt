package com.runcriticon.identidad.application.usecases.account
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.api.events.AlumnoActivado
import com.runcriticon.identidad.api.events.EntrenadorActivado
import com.runcriticon.identidad.application.PasswordPolicy
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.observability.BusinessMetrics
import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.PasswordHistory
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.ports.outbound.security.PasswordHasher
import com.runcriticon.identidad.application.ports.outbound.security.TokenHasher
import com.runcriticon.identidad.application.usecases.authentication.AuthenticateUserCommand
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.events.UserActivated
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.observability.OpenTelemetryHelper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Activación de cuenta por invitación. Es **pública y anónima**: el invitado (entrenador o alumno) se autentica con el
 * token del email, no con la matriz de autorización — por eso, como [AuthenticateUserCommand], NO la consulta y el
 * endpoint que la expone se marca [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired].
 *
 * Verifica y consume la invitación, valida la contraseña ([PasswordPolicy]), fija el hash y pasa la cuenta a `ACTIVO`,
 * registra el histórico, deja asiento de auditoría y construye el domain event [UserActivated], que se traduce al
 * integration event de activación según el rol (`AlumnoActivado`/`EntrenadorActivado`). Devuelve el [Principal] que la
 * capa api guardará en la sesión (auto-login). Todo en una transacción (outbox de Spring Modulith).
 */
@ApplicationService
@NoAuthRequired("Activación pública: el invitado se autentica con el token del email")
class ActivateAccountCommand(
    private val userRepository: UserRepository,
    private val invitationRepository: InvitationRepository,
    private val tokenHasher: TokenHasher,
    private val passwordHasher: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val passwordHistory: PasswordHistory,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
    private val businessMetrics: BusinessMetrics,
) {
    @Transactional
    fun execute(
        rawToken: String,
        password: String,
    ): Either<IdentidadError, Principal> =
        either {
            ensure(rawToken.isNotBlank()) { IdentidadError.InvalidInput("token", "required") }
            val tokenHash = tokenHasher.hash(RawToken(rawToken))
            val invitation = invitationRepository.findByTokenHash(tokenHash)
            ensureNotNull(invitation) { IdentidadError.InvalidInput("token", "mismatch") }

            val user = userRepository.findByIdUnscoped(invitation.clubId, invitation.userId)
            ensureNotNull(user) { IdentidadError.NotFound }
            ensure(user.status == UserStatus.INVITADO) { IdentidadError.Conflict("la cuenta ya está activa") }

            val now = Instant.now()
            val consumed = invitation.consume(tokenHash, now).bind()

            passwordPolicy.validate(password, user).bind()

            val pwdHash = passwordHasher.encode(password)
            val activated = user.activate(pwdHash, now)
            userRepository.save(activated)
            invitationRepository.save(consumed)
            passwordHistory.record(activated.id, activated.clubId, pwdHash, now)

            auditTrail.record(
                AuditEntry(
                    type = AuditEventType.INVITACION_ACTIVADA,
                    actorId = activated.id.value,
                    subjectId = activated.id.value,
                    occurredAt = now,
                ),
            )
            val activation =
                UserActivated(eventId = UuidCreator.getTimeOrderedEpoch(), occurredAt = now, user = activated)
            publishActivated(activation)
            businessMetrics.accountActivated(activated.role)

            Principal(userId = activated.id.value, clubId = activated.clubId.value, role = activated.role)
        }

    /**
     * Traduce el domain event [UserActivated] al integration event correspondiente según el rol. El ADMIN se siembr, no
     * se activa, así que no publica ningún event.
     */
    private fun publishActivated(activation: UserActivated) {
        val user = activation.user
        val event: IntegrationEvent =
            when (user.role) {
                Role.ALUMNO -> alumnoActivado(activation)
                Role.ENTRENADOR -> entrenadorActivado(activation)
                Role.ADMIN -> return
            }
        eventPublisher.publishEvent(event)
    }

    private fun alumnoActivado(activation: UserActivated): AlumnoActivado =
        AlumnoActivado(
            eventId = UuidCreator.getTimeOrderedEpoch(),
            aggregateId = activation.user.id.value,
            occurredAt = activation.occurredAt,
            clubId = activation.user.clubId.value,
            actorId = activation.user.id.value,
            traceparent = OpenTelemetryHelper.actualTraceparent(),
            name = activation.user.name,
            email = activation.user.email.value,
        )

    private fun entrenadorActivado(activation: UserActivated): EntrenadorActivado =
        EntrenadorActivado(
            eventId = UuidCreator.getTimeOrderedEpoch(),
            aggregateId = activation.user.id.value,
            occurredAt = activation.occurredAt,
            clubId = activation.user.clubId.value,
            actorId = activation.user.id.value,
            traceparent = OpenTelemetryHelper.actualTraceparent(),
            name = activation.user.name,
            email = activation.user.email.value,
        )
}
