package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import java.time.Instant
import java.util.UUID

/**
 * Caso de uso de login con contraseña (ADR-0003 D5, D7). Es el punto de entrada de autenticación, así
 * que NO consulta la matriz de autorización (no hay principal todavía); el endpoint que lo expone se
 * marca [com.runcriticon.shared.autorizacion.annotations.NoAuthRequired]. Devuelve un [LoginOutcome]:
 * la sesión a iniciar, o "contraseña caducada" para forzar el cambio (ADR-0003 D7).
 *
 * Los errores son neutros (ADR-0003 D5): no se revela si el email existe.
 */
@ApplicationService
@NoAuthRequired("Login público: punto de entrada de autenticación, no hay principal todavía (ADR-0003 D5)")
class AuthenticateUser(
    private val repository: UserRepository,
    private val hasher: PasswordHasher,
) {
    fun execute(
        clubId: UUID,
        emailRaw: String,
        password: String,
    ): Either<IdentidadError, LoginOutcome> =
        either {
            val user = repository.findByEmail(clubId, Email.of(emailRaw))
            ensureNotNull(user) { IdentidadError.InvalidCredentials }
            ensure(user.isActive()) { IdentidadError.AccountNotActive }
            val storedHash = user.passwordHash
            ensureNotNull(storedHash) { IdentidadError.InvalidCredentials }
            ensure(hasher.matches(password, storedHash)) { IdentidadError.InvalidCredentials }

            if (user.isPasswordExpired(Instant.now())) {
                LoginOutcome.PasswordExpired
            } else {
                LoginOutcome.Authenticated(
                    Principal(userId = user.id.value, clubId = user.clubId, role = user.role),
                )
            }
        }
}
