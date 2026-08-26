package com.runcriticon.identidad.application.usecases.account
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.api.events.AlumnoActivado
import com.runcriticon.identidad.api.events.ConsentimientoConcedido
import com.runcriticon.identidad.api.events.EntrenadorActivado
import com.runcriticon.identidad.application.PasswordPolicy
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.observability.BusinessMetrics
import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.PasswordHistory
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.ports.outbound.security.PasswordHasher
import com.runcriticon.identidad.application.ports.outbound.security.TokenHasher
import com.runcriticon.identidad.application.usecases.authentication.AuthenticateUserCommand
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.consent.ConsentText
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.events.UserActivated
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.User
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
 *
 * **Consentimiento de datos de salud (LAL-128, ADR-0014 D16)**: solo el ALUMNO es el interesado de los datos que
 * captura `seguimiento.reporte_sesion`, así que solo a él se le exige `consentGranted = true` para activar —
 * `ADMIN`/`ENTRENADOR` activan igual sin marcarla. Se captura **dentro de esta misma transacción**, no con una
 * llamada aparte del frontend después de activar: la casilla es condición para que el alumno active, y una segunda
 * petición podría fallar dejando una cuenta activada sin consentimiento.
 */
@ApplicationService
@NoAuthRequired("Activación pública: el invitado se autentica con el token del email")
class ActivateAccountCommand(
    private val userRepository: UserRepository,
    private val invitationRepository: InvitationRepository,
    private val consentRepository: ConsentRepository,
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
        consentGranted: Boolean,
        consentVersion: String?,
        clientIp: String,
        userAgent: String,
    ): Either<IdentidadError, Principal> =
        either {
            ensure(rawToken.isNotBlank()) { IdentidadError.InvalidInput("token", "required") }
            val tokenHash = tokenHasher.hash(RawToken(rawToken))
            val invitation = invitationRepository.findByTokenHash(tokenHash)
            ensureNotNull(invitation) { IdentidadError.InvalidInput("token", "mismatch") }

            val user = userRepository.findByIdUnscoped(invitation.clubId, invitation.userId)
            ensureNotNull(user) { IdentidadError.NotFound }
            ensure(user.status == UserStatus.INVITADO) { IdentidadError.Conflict("la cuenta ya está activa") }
            ensure(user.role != Role.ALUMNO || consentGranted) { IdentidadError.ConsentRequired }
            // Solo se valida la versión para el ALUMNO: es el único rol al que le hace falta el texto
            // de consentimiento — un frontend desactualizado respecto a un `CURRENT_VERSION` que ya
            // cambió en el backend no debe colar una concesión sobre un texto que ya no es el vigente.
            ensure(user.role != Role.ALUMNO || consentVersion == ConsentText.CURRENT_VERSION) {
                IdentidadError.ConsentTextOutdated
            }

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

            if (activated.role == Role.ALUMNO) {
                grantConsent(activated, clientIp, userAgent, now)
            }

            val activation =
                UserActivated(eventId = UuidCreator.getTimeOrderedEpoch(), occurredAt = now, user = activated)
            publishActivated(activation)
            businessMetrics.accountActivated(activated.role)

            Principal(userId = activated.id.value, clubId = activated.clubId.value, role = activated.role)
        }

    /** Solo se llama para el ALUMNO recién activado, con `consentGranted` ya verificado en la guarda de arriba. */
    private fun grantConsent(
        activated: User,
        clientIp: String,
        userAgent: String,
        now: Instant,
    ) {
        val consent =
            Consent.grant(
                userId = activated.id,
                clubId = activated.clubId,
                textVersion = ConsentText.CURRENT_VERSION,
                ip = clientIp,
                userAgent = userAgent,
                now = now,
            )
        consentRepository.save(consent)
        auditTrail.record(
            AuditEntry(
                type = AuditEventType.CONSENTIMIENTO_CONCEDIDO,
                actorId = activated.id.value,
                subjectId = activated.id.value,
                occurredAt = now,
            ),
        )
        eventPublisher.publishEvent(
            ConsentimientoConcedido(
                eventId = UuidCreator.getTimeOrderedEpoch(),
                aggregateId = activated.id.value,
                occurredAt = now,
                clubId = activated.clubId.value,
                actorId = activated.id.value,
                traceparent = OpenTelemetryHelper.actualTraceparent(),
                versionTexto = consent.textVersion,
            ),
        )
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
