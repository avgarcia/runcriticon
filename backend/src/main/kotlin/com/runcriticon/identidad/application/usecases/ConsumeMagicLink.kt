package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.MagicLinkRepository
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.magiclink.MagicLinkPurpose
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.model.Principal
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Consumo de un magic link de login (LAL-11, ADR-0003 D5). Es **público y anónimo**: el usuario se
 * autentica con el token del email, no con la matriz de autorización — por eso, como [ActivateAccount],
 * NO la consulta y el endpoint que lo expone se marca
 * [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired].
 *
 * Verifica y consume el magic link (un solo uso, caducidad 15 min, token timing-safe), exige que la
 * cuenta esté `ACTIVO`, deja asiento de auditoría `MAGIC_LINK_USADO` y devuelve el [Principal] que la
 * capa api guardará en la sesión (auto-login). Todo en una transacción.
 */
@ApplicationService
class ConsumeMagicLink(
    private val userRepository: UserRepository,
    private val magicLinkRepository: MagicLinkRepository,
    private val tokenHasher: TokenHasher,
    private val auditTrail: AuditTrail,
) {
    @Transactional
    fun execute(rawToken: String): Either<IdentidadError, Principal> =
        either {
            ensure(rawToken.isNotBlank()) { IdentidadError.InvalidInput("token", "required") }
            val tokenHash = tokenHasher.hash(RawToken(rawToken))
            val magicLink = magicLinkRepository.findByTokenHash(tokenHash)
            ensureNotNull(magicLink) { IdentidadError.InvalidInput("token", "mismatch") }

            val user = userRepository.findByIdUnscoped(magicLink.clubId, magicLink.userId)
            ensureNotNull(user) { IdentidadError.NotFound }
            ensure(user.isActive()) { IdentidadError.AccountNotActive }

            // consume valida propósito (LOGIN), caducidad (15 min), un solo uso y coincidencia del
            // token (timing-safe): un token de reseteo no vale como login (ADR-0003 D8).
            val now = Instant.now()
            val consumed = magicLink.consume(MagicLinkPurpose.LOGIN, tokenHash, now).bind()
            magicLinkRepository.save(consumed)

            auditTrail.record(
                AuditEntry(
                    type = AuditEventType.MAGIC_LINK_USADO,
                    actorId = user.id.value,
                    subjectId = user.id.value,
                    occurredAt = now,
                ),
            )

            Principal(userId = user.id.value, clubId = user.clubId, role = user.role)
        }
}
