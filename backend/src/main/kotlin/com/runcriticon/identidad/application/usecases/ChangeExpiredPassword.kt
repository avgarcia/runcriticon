package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.PasswordPolicy
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.PasswordHistory
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.model.Principal
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Cambio forzado de contraseña caducada (LAL-10, ADR-0003 D6/D7). Es público y anónimo como el login
 * (no hay principal; el endpoint se marca
 * [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired]): revalida con la contraseña actual
 * —la caducada que el usuario tecleó al intentar entrar— para no abrir un vector de cambio sin
 * conocer credenciales. Solo atiende el caso de caducidad (ADR-0003 D7); el cambio voluntario desde
 * el perfil queda fuera de LAL-10.
 *
 * Valida la nueva contraseña ([PasswordPolicy], D6: 12–128, sin datos personales, no reutilizar las
 * últimas 5), fija el hash, reinicia el reloj de caducidad, registra el histórico y deja asiento de
 * auditoría. Devuelve el [Principal] para que la capa api inicie sesión (auto-login). Todo en una
 * transacción. La invalidación del resto de sesiones del usuario (ADR-0003 D7) se difiere a
 * LAL-12/LAL-13.
 */
@ApplicationService
class ChangeExpiredPassword(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val passwordPolicy: PasswordPolicy,
    private val passwordHistory: PasswordHistory,
    private val auditTrail: AuditTrail,
) {
    @Transactional
    fun execute(
        clubId: UUID,
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

            auditTrail.record(
                AuditEntry(
                    type = AuditEventType.PASSWORD_CAMBIADA,
                    actorId = updated.id.value,
                    subjectId = updated.id.value,
                    occurredAt = now,
                ),
            )

            Principal(userId = updated.id.value, clubId = updated.clubId, role = updated.role)
        }
}
