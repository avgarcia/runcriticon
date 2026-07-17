package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class ListCoachesTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val coach =
            User(
                id = UserId.new(),
                clubId = club,
                email = Email.of("ana@club.local"),
                name = "Ana",
                role = Role.ENTRENADOR,
                passwordHash = "hash",
                status = UserStatus.ACTIVO,
            )

        val userRepository = mockk<UserRepository>(relaxed = true)
        val useCase = ListCoaches(userRepository)

        beforeTest {
            clearMocks(userRepository)
            every { userRepository.listByClubAndRole(club, Role.ENTRENADOR) } returns listOf(coach)
        }

        test("admin lista: devuelve un CoachSummary por entrenador del club") {
            val result = useCase.execute(admin).shouldBeRight()

            result.size shouldBe 1
            val summary = result.first()
            summary.id shouldBe coach.id.value
            summary.name shouldBe coach.name
            summary.email shouldBe coach.email.value
            summary.status shouldBe UserStatus.ACTIVO
        }

        test("actor sin rol ADMIN devuelve Forbidden y no consulta el repositorio") {
            val coachActor = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)

            useCase.execute(coachActor).shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { userRepository.listByClubAndRole(any(), any()) }
        }
    })
