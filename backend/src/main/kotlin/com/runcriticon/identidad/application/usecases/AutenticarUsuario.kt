package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.HashDePassword
import com.runcriticon.identidad.application.ports.RepositorioDeUsuarios
import com.runcriticon.identidad.domain.errores.AutenticacionError
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
class AutenticarUsuario(
    private val usuarios: RepositorioDeUsuarios,
    private val hash: HashDePassword,
) {
    fun ejecutar(
        clubId: UUID,
        emailRaw: String,
        password: String,
    ): Either<AutenticacionError, Principal> =
        either {
            val usuario = usuarios.buscarPorEmail(clubId, Email.de(emailRaw))
            ensureNotNull(usuario) { AutenticacionError.CredencialesInvalidas }
            ensure(usuario.estaActivo()) { AutenticacionError.CuentaNoActiva }
            val hashGuardado = usuario.passwordHash
            ensureNotNull(hashGuardado) { AutenticacionError.CredencialesInvalidas }
            ensure(hash.coincide(password, hashGuardado)) { AutenticacionError.CredencialesInvalidas }
            Principal(userId = usuario.id.valor, clubId = usuario.clubId, rol = usuario.rol)
        }
}
