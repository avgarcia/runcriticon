package com.runcriticon.identidad.infrastructure.bootstrap

import com.runcriticon.identidad.infrastructure.persistencia.UsuarioEntity
import com.runcriticon.identidad.infrastructure.persistencia.UsuarioEntityRepository
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Test unitario de [IdentidadSeeder] (LAL-38): valida el seed del primer admin sin contexto Spring
 * ni base de datos, con MockK. Cubre los tres caminos del `ApplicationRunner`: no-op cuando no hay
 * password de bootstrap, idempotencia si el admin ya existe, y creación con rol/estado correctos.
 *
 * Cada test crea sus propios mocks porque las aserciones usan `verify(exactly = n)`: compartir un
 * único mock a nivel de spec acumularía llamadas entre tests y falsearía los conteos.
 */
class IdentidadSeederTest :
    FunSpec({
        val clubId = "00000000-0000-0000-0000-000000000001"
        val adminEmail = "admin@runcriticon.local"

        fun seeder(
            repo: UsuarioEntityRepository,
            encoder: PasswordEncoder,
            password: String,
        ) = IdentidadSeeder(repo, encoder, adminEmail, password, clubId)

        test("password en blanco: no consulta el repositorio ni persiste") {
            val repo = mockk<UsuarioEntityRepository>()
            val encoder = mockk<PasswordEncoder>()

            seeder(repo, encoder, "").run(DefaultApplicationArguments())

            verify(exactly = 0) { repo.findByClubIdAndNormalizedEmail(any(), any()) }
            verify(exactly = 0) { repo.save(any()) }
        }

        test("admin ya existente: idempotente, no vuelve a persistir") {
            val repo = mockk<UsuarioEntityRepository>()
            val encoder = mockk<PasswordEncoder>()
            every { repo.findByClubIdAndNormalizedEmail(any(), any()) } returns mockk()

            seeder(repo, encoder, "smoke-password-12345").run(DefaultApplicationArguments())

            verify(exactly = 0) { repo.save(any()) }
        }

        test("admin inexistente: crea con rol ADMIN, estado ACTIVO y hash de la password") {
            val repo = mockk<UsuarioEntityRepository>()
            val encoder = mockk<PasswordEncoder>()
            val password = "smoke-password-12345"
            every { repo.findByClubIdAndNormalizedEmail(any(), any()) } returns null
            every { encoder.encode(any()) } returns "hash-argon2"
            every { repo.save(any()) } returns mockk()

            seeder(repo, encoder, password).run(DefaultApplicationArguments())

            verify(exactly = 1) { encoder.encode(password) }
            verify(exactly = 1) {
                repo.save(
                    match<UsuarioEntity> {
                        it.role == "ADMIN" && it.status == "ACTIVO" && it.passwordHash == "hash-argon2"
                    },
                )
            }
        }
    })
