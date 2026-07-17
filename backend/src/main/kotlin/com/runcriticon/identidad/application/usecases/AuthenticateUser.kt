package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant

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
    // Hash de descarte para igualar el coste temporal cuando no hay hash real que verificar (LAL-36).
    // Se computa una vez con el mismo encoder que las contraseñas reales, así hereda sus parámetros vigentes.
    private val decoyPasswordHash: String by lazy { hasher.encode(DECOY_PASSWORD) }

    fun execute(
        clubId: ClubId,
        emailRaw: String,
        password: String,
    ): Either<IdentidadError, LoginOutcome> =
        either {
            val user = repository.findByEmail(clubId, Email.of(emailRaw))
            val storedHash = user?.passwordHash
            // Verify incondicional (real o de descarte) antes de cualquier corte: iguala el timing entre
            // "no existe", "inactivo", "sin contraseña" y "contraseña incorrecta" (ADR-0003 D5, LAL-36).
            val passwordMatches = hasher.matches(password, storedHash ?: decoyPasswordHash)

            ensureNotNull(user) { IdentidadError.InvalidCredentials }
            ensure(user.isActive()) { IdentidadError.AccountNotActive }
            ensureNotNull(storedHash) { IdentidadError.InvalidCredentials }
            ensure(passwordMatches) { IdentidadError.InvalidCredentials }

            // Upgrade-on-login (LAL-58): si el hash guardado usa parámetros más débiles que los
            // vigentes (Argon2Properties), se re-hashea con la contraseña ya verificada. No toca
            // passwordUpdatedAt: la contraseña no cambia, solo su encoding (ADR-0003 D7).
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
