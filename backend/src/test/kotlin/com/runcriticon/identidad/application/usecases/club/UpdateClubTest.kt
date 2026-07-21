package com.runcriticon.identidad.application.usecases.club

import com.runcriticon.identidad.application.ports.outbound.persistence.ClubRepository
import com.runcriticon.identidad.domain.club.Club
import com.runcriticon.identidad.domain.errors.IdentidadError
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
import io.mockk.slot
import io.mockk.verify
import java.util.UUID

class UpdateClubTest :
    FunSpec({
        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)
        val club = Club(id = clubId, name = "Mi club", slug = null)

        val clubRepository = mockk<ClubRepository>(relaxed = true)
        val useCase = UpdateClubCommand(clubRepository)

        beforeTest {
            clearMocks(clubRepository)
            every { clubRepository.findById(clubId) } returns club
        }

        test("admin cambia el nombre: guarda el club renombrado") {
            val savedSlot = slot<Club>()
            every { clubRepository.save(capture(savedSlot)) } returns Unit

            val result = useCase.execute(admin, "Club Runcriticon").shouldBeRight()

            result.name shouldBe "Club Runcriticon"
            savedSlot.captured.name shouldBe "Club Runcriticon"
        }

        test("actor sin rol ADMIN devuelve Forbidden y no produce efectos") {
            val coach = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ENTRENADOR)

            useCase.execute(coach, "Otro nombre").shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { clubRepository.save(any()) }
        }

        test("club inexistente (findById null) devuelve NotFound") {
            every { clubRepository.findById(clubId) } returns null

            useCase.execute(admin, "Otro nombre").shouldBeLeft(IdentidadError.NotFound)

            verify(exactly = 0) { clubRepository.save(any()) }
        }

        test("nombre vacío devuelve InvalidInput y no guarda") {
            useCase.execute(admin, "   ").shouldBeLeft(IdentidadError.InvalidInput("nombre", "blank"))

            verify(exactly = 0) { clubRepository.save(any()) }
        }
    })
