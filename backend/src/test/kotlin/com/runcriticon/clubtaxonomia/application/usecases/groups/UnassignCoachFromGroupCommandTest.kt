package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class UnassignCoachFromGroupCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val grupo = Group.create(club, "Maratón Valencia avanzado").shouldBeRight()
        val entrenador = PersonId.of(UuidCreator.getTimeOrderedEpoch())

        fun groups() =
            InMemoryGroupRepository(
                existing = mapOf(grupo.id to GroupDetail(grupo, members = emptyList(), exclusions = emptyList())),
            )

        test("desvincula al entrenador ya asignado") {
            val repository = groups()
            repository.assignCoach(club, grupo.id, entrenador)

            UnassignCoachFromGroupCommand(repository)
                .execute(admin, grupo.id.value, entrenador.value)
                .shouldBeRight()

            repository.findCoaches(club, grupo.id) shouldBe emptyList()
        }

        test("desvincular a quien no estaba asignado no falla -- idempotente") {
            val repository = groups()

            UnassignCoachFromGroupCommand(repository)
                .execute(admin, grupo.id.value, entrenador.value)
                .shouldBeRight()
        }

        test("no comprueba que el entrenador siga existiendo: limpia asignaciones huerfanas") {
            val repository = groups()
            repository.assignCoach(club, grupo.id, entrenador)

            // Ninguna guarda de CoachLookup en el constructor: si hiciera falta comprobar al entrenador, este test
            // no compilaría sin inyectar un InMemoryCoachLookup.
            UnassignCoachFromGroupCommand(repository)
                .execute(admin, grupo.id.value, entrenador.value)
                .shouldBeRight()
        }

        test("un grupo que no existe da GroupNotFound") {
            UnassignCoachFromGroupCommand(InMemoryGroupRepository())
                .execute(admin, UuidCreator.getTimeOrderedEpoch(), entrenador.value)
                .shouldBeLeft(ClubTaxonomiaError.GroupNotFound)
        }
    })
