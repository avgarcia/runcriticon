package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.PasswordPolicy
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.MagicLinkRepository
import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.PasswordHistory
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.magiclink.MagicLinkPurpose
import com.runcriticon.shared.autorizacion.SessionRevoker
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Consumo del reseteo de contraseña (LAL-12, ADR-0003 D8). Es **público y anónimo**: el usuario se
 * autentica con el token del email, no con la matriz de autorización — por eso, como [ConsumeMagicLink]
 * y [ActivateAccount], NO la consulta y el endpoint que lo expone se marca
 * [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired].
 *
 * Verifica y consume el magic link de propósito `RESETEO` (aislamiento de propósito, un solo uso,
 * caducidad 15 min, token timing-safe), exige que la cuenta esté `ACTIVO`, valida la nueva contraseña
 * ([PasswordPolicy], D6), fija el hash, registra el histórico y **invalida todas las sesiones activas
 * del usuario** ([SessionRevoker], D8: una sesión robada no sobrevive al reseteo). Deja asiento de
 * auditoría `PASSWORD_CAMBIADA` y devuelve el [Principal] que la capa api guardará en la sesión
 * (auto-login). Todo en una transacción.
 *
 * Funciona también para un usuario solo-magic-link que aún no tenga contraseña: fija su primera
 * ([com.runcriticon.identidad.domain.user.User.changePassword] solo exige estado `ACTIVO`, no un hash
 * previo).
 */
@ApplicationService
@NoAuthRequired("Consumo de reseteo: el usuario se autentica con el token del email (ADR-0003 D8)")
class ConsumePasswordReset(
    private val userRepository: UserRepository,
    private val magicLinkRepository: MagicLinkRepository,
    private val tokenHasher: TokenHasher,
    private val passwordHasher: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val passwordHistory: PasswordHistory,
    private val sessionRevoker: SessionRevoker,
    private val auditTrail: AuditTrail,
) {
    @Transactional
    fun execute(
        rawToken: String,
        newPassword: String,
    ): Either<IdentidadError, Principal> =
        either {
            ensure(rawToken.isNotBlank()) { IdentidadError.InvalidInput("token", "required") }
            val tokenHash = tokenHasher.hash(RawToken(rawToken))
            val magicLink = magicLinkRepository.findByTokenHash(tokenHash)
            ensureNotNull(magicLink) { IdentidadError.InvalidInput("token", "mismatch") }

            val user = userRepository.findByIdUnscoped(magicLink.clubId, magicLink.userId)
            ensureNotNull(user) { IdentidadError.NotFound }
            ensure(user.isActive()) { IdentidadError.AccountNotActive }

            // consume valida propósito (RESETEO), caducidad (15 min), un solo uso y token (timing-safe):
            // un token de login no vale como reseteo (ADR-0003 D8).
            val now = Instant.now()
            val consumed = magicLink.consume(MagicLinkPurpose.RESETEO, tokenHash, now).bind()

            passwordPolicy.validate(newPassword, user).bind()

            val newHash = passwordHasher.encode(newPassword)
            val updated = user.changePassword(newHash, now)
            userRepository.save(updated)
            passwordHistory.record(updated.id, updated.clubId, newHash, now)
            magicLinkRepository.save(consumed)

            // ADR-0003 D8: el reseteo invalida todas las sesiones activas del usuario.
            sessionRevoker.revokeAll(updated.id.value)

            auditTrail.record(
                AuditEntry(
                    type = AuditEventType.PASSWORD_CAMBIADA,
                    actorId = updated.id.value,
                    subjectId = updated.id.value,
                    occurredAt = now,
                ),
            )

            Principal(userId = updated.id.value, clubId = updated.clubId.value, role = updated.role)
        }
}
