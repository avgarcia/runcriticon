package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.errores.IdentidadError
import com.runcriticon.identidad.domain.usuario.Email
import com.runcriticon.identidad.domain.usuario.User
import com.runcriticon.identidad.domain.usuario.UserId
import com.runcriticon.identidad.domain.usuario.UserStatus
import com.runcriticon.shared.autorizacion.modelo.Role
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.UUID

class AuthenticateUserTest :
    FunSpec({
        val club = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val repo = mockk<UserRepository>()
        val hasher = mockk<PasswordHasher>()
        val useCase = AuthenticateUser(repo, hasher)

        fun user(
            status: UserStatus = UserStatus.ACTIVO,
            passwordHash: String? = "hash-guardado",
        ) = User(
            id = UserId.new(),
            clubId = club,
            email = Email.of("alumno@club.local"),
            name = "Alumno",
            role = Role.ALUMNO,
            passwordHash = passwordHash,
            status = status,
        )

        test("usuario inexistente devuelve InvalidCredentials") {
            every { repo.findByEmail(any(), any()) } returns null
            useCase
                .execute(club, "x@club.local", "secreta")
                .shouldBeLeft(IdentidadError.InvalidCredentials)
        }

        test("cuenta no activa devuelve AccountNotActive") {
            every { repo.findByEmail(any(), any()) } returns user(status = UserStatus.INVITADO)
            useCase
                .execute(club, "x@club.local", "secreta")
                .shouldBeLeft(IdentidadError.AccountNotActive)
        }

        test("usuario solo-magic-link (sin contraseña) devuelve InvalidCredentials") {
            every { repo.findByEmail(any(), any()) } returns user(passwordHash = null)
            useCase
                .execute(club, "x@club.local", "secreta")
                .shouldBeLeft(IdentidadError.InvalidCredentials)
        }

        test("contraseña incorrecta devuelve InvalidCredentials") {
            every { repo.findByEmail(any(), any()) } returns user()
            every { hasher.matches(any(), any()) } returns false
            useCase
                .execute(club, "x@club.local", "incorrecta")
                .shouldBeLeft(IdentidadError.InvalidCredentials)
        }

        test("credenciales correctas devuelven el Principal del usuario") {
            val expected = user()
            every { repo.findByEmail(any(), any()) } returns expected
            every { hasher.matches(any(), any()) } returns true
            val principal = useCase.execute(club, "alumno@club.local", "correcta").shouldBeRight()
            principal.userId shouldBe expected.id.value
            principal.clubId shouldBe club
            principal.role shouldBe Role.ALUMNO
        }
    })
