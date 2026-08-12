package com.runcriticon.clubtaxonomia.application.usecases.coaches

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.domain.person.CoachWorkload
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ListCoachWorkloadQueryTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)

        val entrenadores =
            listOf(
                CoachWorkload(
                    id = PersonId.of(UuidCreator.getTimeOrderedEpoch()),
                    name = "Carlos Ruiz",
                    email = "carlos@club.test",
                    status = PersonStatus.ACTIVO,
                    groups = emptyList(),
                    totalStudents = 0,
                ),
            )

        test("devuelve lo que resuelve el repositorio") {
            val directory = InMemoryCoachDirectory(entrenadores)

            ListCoachWorkloadQuery(directory).execute(admin).shouldBeRight() shouldBe entrenadores
        }

        test("opera sobre el club del actor") {
            val directory = InMemoryCoachDirectory()

            ListCoachWorkloadQuery(directory).execute(admin)

            directory.calls.single() shouldBe club
        }
    })
