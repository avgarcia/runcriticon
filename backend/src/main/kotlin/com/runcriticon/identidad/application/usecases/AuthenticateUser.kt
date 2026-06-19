package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.errores.IdentidadError
import com.runcriticon.identidad.domain.usuario.Email
import com.runcriticon.shared.autorizacion.anotaciones.ApplicationService
import com.runcriticon.shared.autorizacion.modelo.Principal
import java.util.UUID

/**
 * Caso de uso de login con contraseña (ADR-0003 D5). Es el punto de entrada de autenticación, así
 * que NO consulta la matriz de autorización (no hay principal todavía); el endpoint que lo expone
 * se marca [com.runcriticon.shared.autorizacion.anotaciones.NoAuthRequired]. Devuelve el [Principal] que la
 * capa api guardará en la sesión.
 *
 * Los errores son neutros (ADR-0003 D5): no se revela si el email existe.
 */
@ApplicationService
class AuthenticateUser(
    private val repository: UserRepository,
    private val hasher: PasswordHasher,
) {
    fun execute(
        clubId: UUID,
        emailRaw: String,
        password: String,
    ): Either<IdentidadError, Principal> =
        either {
            val user = repository.findByEmail(clubId, Email.of(emailRaw))
            ensureNotNull(user) { IdentidadError.InvalidCredentials }
            ensure(user.isActive()) { IdentidadError.AccountNotActive }
            val storedHash = user.passwordHash
            ensureNotNull(storedHash) { IdentidadError.InvalidCredentials }
            ensure(hasher.matches(password, storedHash)) { IdentidadError.InvalidCredentials }
            Principal(userId = user.id.value, clubId = user.clubId, role = user.role)
        }
}
