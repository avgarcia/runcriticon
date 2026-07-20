package com.runcriticon.identidad.application.usecases.magiclink

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.application.ports.inbound.MagicLinkEmailRequested
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
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.magiclink.MagicLink
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
 * Solicitud de magic link de login. Público y anónimo (no hay sesión): el endpoint que lo expone se marca
 * [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired]. Si el email corresponde a una cuenta `ACTIVO`,
 * emite un token de un solo uso (15 min), lo persiste hasheado, dispara el email vía outbox y deja asiento de auditoría
 * `MAGIC_LINK_EMITIDO`.
 *
 * **Rate-limiting**: antes del lookup se comprueban, con el email tal cual lo envía el cliente, el cooldown progresivo
 * y los límites por cuenta e IP. Si se alcanza alguno, la respuesta es **idéntica** a la del camino feliz (202 neutro):
 * NO se envía email y se registra un asiento `MAGIC_LINK_RATE_LIMITED` con `email_hash` + `ip`. Chequear con el email
 * presentado (no con el usuario resuelto) evita revelar si la cuenta existe y frena el spam dirigido a la víctima.
 *
 * **Respuesta neutra**: si el email no existe o la cuenta no está activa, NO envía nada y devuelve igualmente
 * `Right(Unit)`.
 */
@ApplicationService
@NoAuthRequired("Solicitud de magic link: entrada anónima con respuesta neutra (ADR-0003 D5)")
class RequestMagicLinkCommand(
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
                metrics.blocked("magic_link", blocked)
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
            rateLimiter.tryConsume(RateLimitScope.MAGIC_LINK_ACCOUNT, email) is Limited -> "cuenta"
            rateLimiter.tryConsume(RateLimitScope.MAGIC_LINK_IP, clientIp) is Limited -> "ip"
            else -> null
        }

    private fun cooldownKey(email: String): String = "magiclink:$email"

    private fun recordRateLimited(
        email: String,
        clientIp: String,
    ) {
        auditTrail.record(
            AuditEntry(
                type = AuditEventType.MAGIC_LINK_RATE_LIMITED,
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
            MagicLink.issue(user.id, user.clubId, tokenHasher.hash(rawToken), MagicLinkPurpose.LOGIN, now)
        magicLinkRepository.save(magicLink)

        eventPublisher.publishEvent(
            MagicLinkEmailRequested(
                to = user.email,
                recipientName = user.name,
                rawToken = rawToken,
                expiresAt = magicLink.expiresAt,
                clubId = user.clubId.value,
            ),
        )

        auditTrail.record(
            AuditEntry(
                type = AuditEventType.MAGIC_LINK_EMITIDO,
                actorId = user.id.value,
                subjectId = user.id.value,
                occurredAt = now,
            ),
        )
    }
}
