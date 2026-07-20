package com.runcriticon.identidad.application.usecases.password

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.PasswordPolicy
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.PasswordHistory
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.ports.outbound.security.PasswordHasher
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.SessionRevoker
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Cambio forzado de contraseña caducada. Es público y anónimo como el login (no hay principal; el endpoint se marca
 * [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired]): revalida con la contraseña actual —la caducada que
 * el usuario tecleó al intentar entrar— para no abrir un vector de cambio sin conocer credenciales. Solo atiende el
 * caso de caducidad; el cambio voluntario desde el perfil queda fuera.
 *
 * Valida la nueva contraseña ([PasswordPolicy]: 12–128, sin datos personales, no reutilizar las últimas 5), fija el
 * hash, reinicia el reloj de caducidad, registra el histórico y deja asiento de auditoría. Invalida todas las sesiones
 * activas del usuario ([SessionRevoker]: una sesión robada no sobrevive al cambio); el auto-login posterior de la capa
 * api crea la única sesión superviviente.
 * Devuelve el [Principal] para que la capa api inicie sesión (auto-login). Todo en una transacción.
 */
@ApplicationService
@NoAuthRequired("Cambio de contraseña caducada: revalida con la contraseña actual, no hay principal")
class ChangeExpiredPasswordCommand(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val passwordHistory: PasswordHistory,
    private val sessionRevoker: SessionRevoker,
    private val auditTrail: AuditTrail,
) {
    @Transactional
    fun execute(
        clubId: ClubId,
        emailRaw: String,
        currentPassword: String,
        newPassword: String,
    ): Either<IdentidadError, Principal> =
        either {
            val user = userRepository.findByEmail(clubId, Email.of(emailRaw))
            ensureNotNull(user) { IdentidadError.InvalidCredentials }
            ensure(user.isActive()) { IdentidadError.AccountNotActive }
            val storedHash = user.passwordHash
            ensureNotNull(storedHash) { IdentidadError.InvalidCredentials }
            ensure(passwordHasher.matches(currentPassword, storedHash)) { IdentidadError.InvalidCredentials }

            val now = Instant.now()
            ensure(user.isPasswordExpired(now)) { IdentidadError.Conflict("la contraseña no está caducada") }

            passwordPolicy.validate(newPassword, user).bind()

            val newHash = passwordHasher.encode(newPassword)
            val updated = user.changePassword(newHash, now)
            userRepository.save(updated)
            passwordHistory.record(updated.id, updated.clubId, newHash, now)

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
