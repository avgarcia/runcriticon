package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.Instant
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
            passwordUpdatedAt: Instant? = Instant.now(),
        ) = User(
            id = UserId.new(),
            clubId = club,
            email = Email.of("alumno@club.local"),
            name = "Alumno",
            role = Role.ALUMNO,
            passwordHash = passwordHash,
            status = status,
            passwordUpdatedAt = passwordUpdatedAt,
        )

        test("usuario inexistente devuelve InvalidCredentials") {
            every { repo.findByEmail(any(), any()) } returns null
            useCase.execute(club, "x@club.local", "secreta").shouldBeLeft(IdentidadError.InvalidCredentials)
        }

        test("cuenta no activa devuelve AccountNotActive") {
            every { repo.findByEmail(any(), any()) } returns user(status = UserStatus.INVITADO)
            useCase.execute(club, "x@club.local", "secreta").shouldBeLeft(IdentidadError.AccountNotActive)
        }

        test("usuario solo-magic-link (sin contraseña) devuelve InvalidCredentials") {
            every { repo.findByEmail(any(), any()) } returns user(passwordHash = null)
            useCase.execute(club, "x@club.local", "secreta").shouldBeLeft(IdentidadError.InvalidCredentials)
        }

        test("contraseña incorrecta devuelve InvalidCredentials") {
            every { repo.findByEmail(any(), any()) } returns user()
            every { hasher.matches(any(), any()) } returns false
            useCase.execute(club, "x@club.local", "incorrecta").shouldBeLeft(IdentidadError.InvalidCredentials)
        }

        test("credenciales correctas y contraseña vigente devuelven Authenticated con el Principal") {
            val expected = user(passwordUpdatedAt = Instant.now())
            every { repo.findByEmail(any(), any()) } returns expected
            every { hasher.matches(any(), any()) } returns true

            val outcome =
                useCase
                    .execute(club, "alumno@club.local", "correcta")
                    .shouldBeRight()
                    .shouldBeInstanceOf<LoginOutcome.Authenticated>()
            outcome.principal.userId shouldBe expected.id.value
            outcome.principal.clubId shouldBe club
            outcome.principal.role shouldBe Role.ALUMNO
        }

        test("contraseña caducada (>90 días) devuelve PasswordExpired sin Principal") {
            every { repo.findByEmail(any(), any()) } returns
                user(passwordUpdatedAt = Instant.now().minus(Duration.ofDays(91)))
            every { hasher.matches(any(), any()) } returns true

            useCase.execute(club, "alumno@club.local", "correcta").shouldBeRight() shouldBe LoginOutcome.PasswordExpired
        }
    })
