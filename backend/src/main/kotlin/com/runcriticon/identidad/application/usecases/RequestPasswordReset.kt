package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.EmailHasher
import com.runcriticon.identidad.application.ports.MagicLinkRepository
import com.runcriticon.identidad.application.ports.PasswordResetEmailRequested
import com.runcriticon.identidad.application.ports.TokenGenerator
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.application.ratelimit.ProgressiveThrottle
import com.runcriticon.identidad.application.ratelimit.RateLimitDecision.Limited
import com.runcriticon.identidad.application.ratelimit.RateLimitMetrics
import com.runcriticon.identidad.application.ratelimit.RateLimitScope
import com.runcriticon.identidad.application.ratelimit.RateLimiter
import com.runcriticon.identidad.application.ratelimit.ThrottleProfile
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.magiclink.MagicLink
import com.runcriticon.identidad.domain.magiclink.MagicLinkPurpose
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Solicitud de reseteo de contraseña (LAL-12, ADR-0003 D8). Público y anónimo (no hay sesión): el
 * endpoint que lo expone se marca [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired]. Si
 * el email corresponde a una cuenta `ACTIVO`, emite un magic link de propósito `RESETEO` de un solo
 * uso (15 min), lo persiste hasheado, dispara el email vía outbox y deja asiento de auditoría
 * `RESETEO_INICIADO`.
 *
 * **Rate-limiting (ADR-0003 D12, LAL-35)**: espejo de [RequestMagicLink] — cooldown progresivo +
 * límites por cuenta e IP con el email presentado; al alcanzar el límite, 202 neutro sin envío y
 * asiento `RESETEO_RATE_LIMITED` con `email_hash` + `ip`.
 *
 * **Respuesta neutra** (ADR-0003 D8): si el email no existe o la cuenta no está activa, NO envía nada
 * y devuelve igualmente `Right(Unit)`.
 */
@ApplicationService
class RequestPasswordReset(
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
        clubId: UUID,
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
        val now = Instant.now()
        val rawToken = tokenGenerator.generate()
        val magicLink =
            MagicLink.issue(user.id, user.clubId, tokenHasher.hash(rawToken), MagicLinkPurpose.RESETEO, now)
        magicLinkRepository.save(magicLink)

        eventPublisher.publishEvent(
            PasswordResetEmailRequested(
                to = user.email,
                recipientName = user.name,
                rawToken = rawToken,
                expiresAt = magicLink.expiresAt,
            ),
        )

        auditTrail.record(
            AuditEntry(
                type = AuditEventType.RESETEO_INICIADO,
                actorId = user.id.value,
                subjectId = user.id.value,
                occurredAt = now,
            ),
        )
    }
}
