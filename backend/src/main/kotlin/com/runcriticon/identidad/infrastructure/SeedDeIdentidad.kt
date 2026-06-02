package com.runcriticon.identidad.infrastructure

import com.github.f4b6a3.uuid.UuidCreator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Semilla del primer admin del club (ADR-0003 D3): alta por arranque parametrizado y versionado,
 * **no** por INSERT a mano. Solo en `local`/`staging`, e idempotente. Si no hay contraseña de
 * bootstrap configurada, no hace nada (seguro por defecto: producción no siembra credenciales).
 * El hash se genera con el [PasswordEncoder] real (Argon2id), de ahí que sea código y no SQL.
 */
@Component
@Profile("local", "staging")
class SeedDeIdentidad(
    private val usuarios: UsuarioJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${runcriticon.bootstrap.admin-email:admin@runcriticon.local}")
    private val adminEmail: String,
    @Value("\${runcriticon.bootstrap.admin-password:}")
    private val adminPassword: String,
    @Value("\${runcriticon.bootstrap.club-id:00000000-0000-0000-0000-000000000001}")
    private val clubId: String,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (adminPassword.isBlank()) return

        val club = UUID.fromString(clubId)
        val emailNormalizado = adminEmail.trim().lowercase()
        if (usuarios.findByClubIdAndEmailNormalizado(club, emailNormalizado) != null) return

        val ahora = Instant.now()
        usuarios.save(
            UsuarioEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                clubId = club,
                email = adminEmail.trim(),
                emailNormalizado = emailNormalizado,
                nombre = "Administrador",
                rol = "ADMIN",
                passwordHash = passwordEncoder.encode(adminPassword),
                estado = "ACTIVO",
                creadoEn = ahora,
                modificadoEn = ahora,
            ),
        )
    }
}
