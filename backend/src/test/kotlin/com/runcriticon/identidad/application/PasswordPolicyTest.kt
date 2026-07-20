package com.runcriticon.identidad.application

import com.runcriticon.identidad.application.ports.outbound.persistence.PasswordHistory
import com.runcriticon.identidad.application.ports.outbound.security.PasswordHasher
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.util.UUID

class PasswordPolicyTest :
    FunSpec({
        val passwordHasher = mockk<PasswordHasher>()
        val passwordHistory = mockk<PasswordHistory>(relaxed = true)
        val policy = PasswordPolicy(passwordHasher, passwordHistory)

        val user =
            User.newInvited(ClubId.of(UUID.randomUUID()), Email.of("marta@club.local"), "Marta Ruiz", Role.ALUMNO)

        beforeTest {
            clearMocks(passwordHasher, passwordHistory)
            every { passwordHistory.recentHashes(any(), any()) } returns emptyList()
            every { passwordHasher.matches(any(), any()) } returns false
        }

        fun reasonOf(result: arrow.core.Either<IdentidadError, Unit>): String =
            result.shouldBeLeft().shouldBeInstanceOf<IdentidadError.InvalidInput>().reason

        test("una contraseña que cumple todo es válida") {
            policy.validate("clave-clave-clave", user).shouldBeRight()
        }

        test("menos de 12 caracteres devuelve too_short") {
            reasonOf(policy.validate("corta-11chr", user)) shouldBe "too_short"
        }

        test("más de 128 caracteres devuelve too_long") {
            reasonOf(policy.validate("a".repeat(129), user)) shouldBe "too_long"
        }

        test("contener el nombre devuelve contains_personal_data") {
            reasonOf(policy.validate("zzzz-Marta-9999", user)) shouldBe "contains_personal_data"
        }

        test("contener la parte local del email devuelve contains_personal_data") {
            val other =
                User.newInvited(ClubId.of(UUID.randomUUID()), Email.of("carlos@club.local"), "Zzz Www", Role.ENTRENADOR)
            reasonOf(policy.validate("aaaa-carlos-9999", other)) shouldBe "contains_personal_data"
        }

        test("reutilizar una de las últimas contraseñas devuelve reused") {
            every { passwordHistory.recentHashes(user.id, 5) } returns listOf("old-hash")
            every { passwordHasher.matches("clave-clave-clave", "old-hash") } returns true

            reasonOf(policy.validate("clave-clave-clave", user)) shouldBe "reused"
        }
    })
