package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.api.events.AlumnoActivado
import com.runcriticon.identidad.api.events.EntrenadorActivado
import com.runcriticon.identidad.application.PasswordPolicy
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.InvitationRepository
import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.PasswordHistory
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.observability.OpenTelemetryHelper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Activación de cuenta por invitación (LAL-9, ADR-0003 D4/D6). Es **pública y anónima**: el invitado
 * (entrenador o alumno) se autentica con el token del email, no con la matriz de autorización — por
 * eso, como [AuthenticateUser], NO la consulta y el endpoint que la expone se marca
 * [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired].
 *
 * Verifica y consume la invitación, valida la contraseña ([PasswordPolicy]), fija el hash y pasa la
 * cuenta a `ACTIVO`, registra el histórico, deja asiento de auditoría y publica el integration event
 * de activación según el rol (`AlumnoActivado`/`EntrenadorActivado`). Devuelve el [Principal] que la
 * capa api guardará en la sesión (auto-login). Todo en una transacción (outbox de Spring Modulith).
 */
@ApplicationService
@NoAuthRequired("Activación pública: el invitado se autentica con el token del email (ADR-0003 D4)")
class ActivateAccount(
    private val userRepository: UserRepository,
    private val invitationRepository: InvitationRepository,
    private val tokenHasher: TokenHasher,
    private val passwordHasher: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val passwordHistory: PasswordHistory,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
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

            // Se valida el estado de la cuenta ANTES de consumir el token (revisión PR #163).
            val user = userRepository.findByIdUnscoped(invitation.clubId, invitation.userId)
            ensureNotNull(user) { IdentidadError.NotFound }
            ensure(user.status == UserStatus.INVITADO) { IdentidadError.Conflict("la cuenta ya está activa") }

            // consume valida caducidad (7 días), un solo uso y coincidencia del token (timing-safe).
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
            publishActivated(activated, now)

            Principal(userId = activated.id.value, clubId = activated.clubId.value, role = activated.role)
        }

    /** Publica el evento de activación según el rol. El ADMIN se siembra (LAL-6), no se activa. */
    private fun publishActivated(
        user: User,
        now: Instant,
    ) {
        val event: IntegrationEvent =
            when (user.role) {
                Role.ALUMNO -> alumnoActivado(user, now)
                Role.ENTRENADOR -> entrenadorActivado(user, now)
                Role.ADMIN -> return
            }
        eventPublisher.publishEvent(event)
    }

    private fun alumnoActivado(
        user: User,
        now: Instant,
    ): AlumnoActivado =
        AlumnoActivado(
            eventId = UUID.randomUUID(),
            aggregateId = user.id.value,
            occurredAt = now,
            clubId = user.clubId.value,
            actorId = user.id.value,
            traceparent = OpenTelemetryHelper.actualTraceparent(),
            name = user.name,
            email = user.email.value,
        )

    private fun entrenadorActivado(
        user: User,
        now: Instant,
    ): EntrenadorActivado =
        EntrenadorActivado(
            eventId = UUID.randomUUID(),
            aggregateId = user.id.value,
            occurredAt = now,
            clubId = user.clubId.value,
            actorId = user.id.value,
            traceparent = OpenTelemetryHelper.actualTraceparent(),
            name = user.name,
            email = user.email.value,
        )
}
