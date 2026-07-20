package com.runcriticon.identidad.application.usecases.password
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.application.ports.inbound.PasswordResetEmailRequested
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.MagicLinkRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.ports.outbound.security.EmailHasher
import com.runcriticon.identidad.application.ports.outbound.security.TokenGenerator
import com.runcriticon.identidad.application.ports.outbound.security.TokenHasher
import com.runcriticon.identidad.application.ratelimit.ProgressiveThrottle
import com.runcriticon.identidad.application.ratelimit.RateLimitDecision.Limited
import com.runcriticon.identidad.application.ratelimit.RateLimitMetrics
import com.runcriticon.identidad.application.ratelimit.RateLimitScope
import com.runcriticon.identidad.application.ratelimit.RateLimiter
import com.runcriticon.identidad.application.ratelimit.ThrottleProfile
import com.runcriticon.identidad.application.usecases.magiclink.RequestMagicLinkCommand
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.magiclink.MagicLink
import com.runcriticon.identidad.domain.magiclink.MagicLink.Companion.issue
import com.runcriticon.identidad.domain.magiclink.MagicLinkPurpose
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Solicitud de reseteo de contraseña. Público y anónimo (no hay sesión): el endpoint que lo expone se marca
 * [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired]. Si el email corresponde a una cuenta `ACTIVO`,
 * emite un magic link de propósito `RESETEO` de un solo uso (15 min), lo persiste hasheado, dispara el email vía outbox
 * y deja asiento de auditoría `RESETEO_INICIADO`.
 *
 * **Rate-limiting**: espejo de [RequestMagicLinkCommand] — cooldown progresivo + límites por cuenta e IP con el email
 * presentado; al alcanzar el límite, 202 neutro sin envío y asiento `RESETEO_RATE_LIMITED` con `email_hash` + `ip`.
 *
 * **Respuesta neutra**: si el email no existe o la cuenta no está activa, NO envía nada y devuelve igualmente
 * `Right(Unit)`.
 */
@ApplicationService
@NoAuthRequired("Solicitud de reseteo: entrada anónima con respuesta neutra (ADR-0003 D8)")
class RequestPasswordResetCommand(
    private val userRepository: UserRepository,
    private val magicLinkRepository: MagicLinkRepository,
    private val tokenGenerator: TokenGenerator,
    private val tokenHasher: TokenHasher,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
    private val rateLimiter: RateLimiter,
    private val throttle: ProgressiveThrottle,
    private val metrics: RateLimitMetrics,
    private val emailHasher: EmailHasher,
) {
    @Transactional
    fun execute(
        clubId: ClubId,
        emailRaw: String,
        clientIp: String,
    ): Either<IdentidadError, Unit> =
        either {
            ensure(emailRaw.contains('@')) { IdentidadError.InvalidInput("email", "invalid") }
            val email = emailRaw.trim().lowercase()

            val blocked = blockedDimension(email, clientIp)
            if (blocked != null) {
                metrics.blocked("reseteo", blocked)
                recordRateLimited(email, clientIp)
                return@either
            }

            val user = userRepository.findByEmail(clubId, Email.of(emailRaw))
            if (user != null && user.isActive()) {
                issueAndSend(user)
            }
            throttle.penalize(ThrottleProfile.EMAIL_COOLDOWN, cooldownKey(email))
        }

    private fun blockedDimension(
        email: String,
        clientIp: String,
    ): String? =
        when {
            throttle.check(ThrottleProfile.EMAIL_COOLDOWN, cooldownKey(email)) != null -> "cooldown"
            rateLimiter.tryConsume(RateLimitScope.PASSWORD_RESET_ACCOUNT, email) is Limited -> "cuenta"
            rateLimiter.tryConsume(RateLimitScope.PASSWORD_RESET_IP, clientIp) is Limited -> "ip"
            else -> null
        }

    private fun cooldownKey(email: String): String = "reseteo:$email"

    private fun recordRateLimited(
        email: String,
        clientIp: String,
    ) {
        auditTrail.record(
            AuditEntry(
                type = AuditEventType.RESETEO_RATE_LIMITED,
                actorId = null,
                subjectId = null,
                occurredAt = Instant.now(),
                metadata = mapOf("email_hash" to emailHasher.hash(email), "ip" to clientIp),
            ),
        )
    }

    private fun issueAndSend(user: User) {
        val rawToken = tokenGenerator.generate()
        val magicLink =
            issue(
                userId = user.id,
                clubId = user.clubId,
                tokenHash = tokenHasher.hash(rawToken),
                proposito = MagicLinkPurpose.RESETEO,
                now = Instant.now()
            )
        magicLinkRepository.save(magicLink)

        eventPublisher.publishEvent(
            PasswordResetEmailRequested(
                to = user.email,
                recipientName = user.name,
                rawToken = rawToken,
                expiresAt = magicLink.expiresAt,
                clubId = user.clubId.value,
            ),
        )

        auditTrail.record(
            AuditEntry(
                type = AuditEventType.RESETEO_INICIADO,
                actorId = user.id.value,
                subjectId = user.id.value,
                occurredAt = Instant.now(),
            ),
        )
    }
}
