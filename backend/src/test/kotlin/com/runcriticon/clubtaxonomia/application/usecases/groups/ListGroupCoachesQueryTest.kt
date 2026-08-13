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

class ListGroupCoachesQueryTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val grupo = Group.create(club, "Maratón Valencia avanzado").shouldBeRight()
        val entrenador = PersonId.of(UuidCreator.getTimeOrderedEpoch())

        test("devuelve los entrenadores asignados") {
            val repository =
                InMemoryGroupRepository(
                    existing = mapOf(grupo.id to GroupDetail(grupo, members = emptyList(), exclusions = emptyList())),
                )
            repository.assignCoach(club, grupo.id, entrenador)

            ListGroupCoachesQuery(repository).execute(admin, grupo.id.value).shouldBeRight().map { it.id } shouldBe
                listOf(entrenador)
        }

        test("un grupo que no existe da GroupNotFound") {
            ListGroupCoachesQuery(InMemoryGroupRepository())
                .execute(admin, UuidCreator.getTimeOrderedEpoch())
                .shouldBeLeft(ClubTaxonomiaError.GroupNotFound)
        }
    })
