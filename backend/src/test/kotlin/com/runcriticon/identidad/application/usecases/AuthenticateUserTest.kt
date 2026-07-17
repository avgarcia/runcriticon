package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.PasswordHasher
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AuthenticateUserTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val repo = mockk<UserRepository>()
        val hasher = mockk<PasswordHasher>()
        val useCase = AuthenticateUser(repo, hasher)

        // Los mocks son compartidos entre tests (FunSpec, SingleInstance): limpiar el historial de
        // llamadas antes de cada test para que `verify(exactly = ...)` no arrastre invocaciones previas.
        beforeTest { clearMocks(repo, hasher) }

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

        test("usuario inexistente devuelve InvalidCredentials y ejecuta el verify de descarte (LAL-36)") {
            every { repo.findByEmail(any(), any()) } returns null
            every { hasher.encode(any()) } returns "hash-decoy"
            every { hasher.matches(any(), any()) } returns false

            useCase.execute(club, "x@club.local", "secreta").shouldBeLeft(IdentidadError.InvalidCredentials)

            verify(exactly = 1) { hasher.matches(any(), any()) }
        }

        test("cuenta no activa devuelve AccountNotActive") {
            every { repo.findByEmail(any(), any()) } returns user(status = UserStatus.INVITADO)
            every { hasher.matches(any(), any()) } returns false
            useCase.execute(club, "x@club.local", "secreta").shouldBeLeft(IdentidadError.AccountNotActive)
        }

        test("usuario sin contraseña (solo magic-link) devuelve InvalidCredentials y ejecuta el verify de descarte") {
            every { repo.findByEmail(any(), any()) } returns user(passwordHash = null)
            every { hasher.encode(any()) } returns "hash-decoy"
            every { hasher.matches(any(), any()) } returns false

            useCase.execute(club, "x@club.local", "secreta").shouldBeLeft(IdentidadError.InvalidCredentials)

            verify(exactly = 1) { hasher.matches(any(), any()) }
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
            every { hasher.needsRehash(any()) } returns false

            val outcome =
                useCase
                    .execute(club, "alumno@club.local", "correcta")
                    .shouldBeRight()
                    .shouldBeInstanceOf<LoginOutcome.Authenticated>()
            outcome.principal.userId shouldBe expected.id.value
            outcome.principal.clubId shouldBe club.value
            outcome.principal.role shouldBe Role.ALUMNO
        }

        test("contraseña caducada (>90 días) devuelve PasswordExpired sin Principal") {
            every { repo.findByEmail(any(), any()) } returns
                user(passwordUpdatedAt = Instant.now().minus(Duration.ofDays(91)))
            every { hasher.matches(any(), any()) } returns true
            every { hasher.needsRehash(any()) } returns false

            useCase.execute(club, "alumno@club.local", "correcta").shouldBeRight() shouldBe LoginOutcome.PasswordExpired
        }

        test("hash con parámetros antiguos se re-hashea tras el login sin tocar passwordUpdatedAt (LAL-58)") {
            val stored = user(passwordUpdatedAt = Instant.now().minus(Duration.ofDays(30)))
            every { repo.findByEmail(any(), any()) } returns stored
            every { hasher.matches(any(), any()) } returns true
            every { hasher.needsRehash("hash-guardado") } returns true
            every { hasher.encode("correcta") } returns "hash-recalculado"
            val saved = slot<User>()
            every { repo.save(capture(saved)) } just Runs

            useCase.execute(club, "alumno@club.local", "correcta").shouldBeRight()

            saved.captured.passwordHash shouldBe "hash-recalculado"
            // El reloj de caducidad (ADR-0003 D7) no se reinicia: la contraseña es la misma.
            saved.captured.passwordUpdatedAt shouldBe stored.passwordUpdatedAt
        }

        test("hash con parámetros vigentes no se re-hashea ni persiste nada (LAL-58)") {
            every { repo.findByEmail(any(), any()) } returns user()
            every { hasher.matches(any(), any()) } returns true
            every { hasher.needsRehash(any()) } returns false

            useCase.execute(club, "alumno@club.local", "correcta").shouldBeRight()

            verify(exactly = 0) { repo.save(any()) }
        }
    })
