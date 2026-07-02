package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.MagicLinkRepository
import com.runcriticon.identidad.application.ports.PasswordResetEmailRequested
import com.runcriticon.identidad.application.ports.TokenGenerator
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
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
 * **Respuesta neutra** (ADR-0003 D8): si el email no existe o la cuenta no está activa, NO envía nada
 * y devuelve igualmente `Right(Unit)` — la capa api responde lo mismo en ambos casos para no revelar
 * si una cuenta existe. El rate-limiting de envíos (ADR-0003 D12) se aborda en LAL-35. Espejo de
 * [RequestMagicLink] con propósito `RESETEO`.
 */
@ApplicationService
class RequestPasswordReset(
    private val userRepository: UserRepository,
    private val magicLinkRepository: MagicLinkRepository,
    private val tokenGenerator: TokenGenerator,
    private val tokenHasher: TokenHasher,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun execute(
        clubId: UUID,
        emailRaw: String,
    ): Either<IdentidadError, Unit> =
        either {
            ensure(emailRaw.contains('@')) { IdentidadError.InvalidInput("email", "invalid") }
            val user = userRepository.findByEmail(clubId, Email.of(emailRaw))
            // Respuesta neutra: solo se emite para cuentas activas; en otro caso no se hace nada.
            if (user != null && user.isActive()) {
                issueAndSend(user)
            }
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
