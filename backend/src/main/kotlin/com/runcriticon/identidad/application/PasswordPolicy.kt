package com.runcriticon.identidad.application

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.application.ports.outbound.persistence.PasswordHistory
import com.runcriticon.identidad.application.ports.outbound.security.PasswordHasher
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.User
import org.springframework.stereotype.Component

/**
 * Política de contraseñas (ADR-0003 D6): longitud 12–128, no contener datos personales
 * (email/nombre) y no reutilizar las últimas [HISTORY_SIZE]. **Sin HIBP**: aplazado fuera del MVP,
 * disparador de reapertura en ADR-0015. Reutilizable por la activación (LAL-9) y el reseteo
 * (LAL-12). Los `reason` son estables para que la capa REST/UI los traduzca; el frontend valida en
 * paralelo (UX), pero el backend es la fuente de verdad.
 */
@Component
class PasswordPolicy(
    private val passwordHasher: PasswordHasher,
    private val passwordHistory: PasswordHistory,
) {
    fun validate(
        rawPassword: String,
        user: User,
    ): Either<IdentidadError, Unit> =
        either {
            ensure(rawPassword.length >= MIN_LENGTH) { invalid("too_short") }
            ensure(rawPassword.length <= MAX_LENGTH) { invalid("too_long") }
            ensure(!containsPersonalData(rawPassword, user)) { invalid("contains_personal_data") }
            ensure(!isReused(rawPassword, user)) { invalid("reused") }
        }

    /** Rechaza si la contraseña contiene el email (parte local) o un token del nombre (≥ 3 caracteres). */
    private fun containsPersonalData(
        rawPassword: String,
        user: User,
    ): Boolean {
        val lower = rawPassword.lowercase()
        val tokens =
            (user.name.split(' ', '-', '.') + user.email.value.substringBefore('@'))
                .map { it.trim().lowercase() }
                .filter { it.length >= PERSONAL_TOKEN_MIN }
        return tokens.any { lower.contains(it) }
    }

    /** Rechaza si coincide con alguna de las últimas [HISTORY_SIZE] (verify Argon2id contra cada hash). */
    private fun isReused(
        rawPassword: String,
        user: User,
    ): Boolean =
        passwordHistory
            .recentHashes(user.id, HISTORY_SIZE)
            .any { passwordHasher.matches(rawPassword, it) }

    private companion object {
        const val MIN_LENGTH = 12
        const val MAX_LENGTH = 128
        const val HISTORY_SIZE = 5
        const val PERSONAL_TOKEN_MIN = 3

        fun invalid(reason: String): IdentidadError = IdentidadError.InvalidInput("password", reason)
    }
}
