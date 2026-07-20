package com.runcriticon.identidad.application.usecases.authentication

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.ports.outbound.security.PasswordHasher
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant

/**
 * Caso de uso de login con contraseña. Es el punto de entrada de autenticación, así que NO consulta la matriz de
 * autorización (no hay principal todavía); el endpoint que lo expone se marca
 * [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired]. Devuelve un [LoginOutcome]: la sesión a iniciar, o
 * "contraseña caducada" para forzar el cambio.
 *
 * Los errores son neutros: no se revela si el email existe.
 */
@ApplicationService
@NoAuthRequired("Login público: punto de entrada de autenticación, no hay principal todavía")
class AuthenticateUserCommand(
    private val repository: UserRepository,
    private val hasher: PasswordHasher,
) {
    private val decoyPasswordHash: String by lazy { hasher.encode(DECOY_PASSWORD) }

    fun execute(
        clubId: ClubId,
        emailRaw: String,
        password: String,
    ): Either<IdentidadError, LoginOutcome> =
        either {
            val user = repository.findByEmail(clubId, Email.of(emailRaw))
            val storedHash = user?.passwordHash

            val passwordMatches = hasher.matches(password, storedHash ?: decoyPasswordHash)

            ensureNotNull(user) { IdentidadError.InvalidCredentials }
            ensure(user.isActive()) { IdentidadError.AccountNotActive }
            ensureNotNull(storedHash) { IdentidadError.InvalidCredentials }
            ensure(passwordMatches) { IdentidadError.InvalidCredentials }

            if (hasher.needsRehash(storedHash)) {
                repository.save(user.rehashPassword(hasher.encode(password)))
            }

            if (user.isPasswordExpired(Instant.now())) {
                LoginOutcome.PasswordExpired
            } else {
                LoginOutcome.Authenticated(
                    Principal(userId = user.id.value, clubId = user.clubId.value, role = user.role),
                )
            }
        }

    private companion object {
        const val DECOY_PASSWORD = "decoy-password-solo-para-igualar-timing-nunca-coincide-0000"
    }
}
