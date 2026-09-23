package com.runcriticon.clubtaxonomia.application.usecases.coaches

import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.testing.PrincipalBuilder
import com.runcriticon.testing.TestClubs
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

/**
 * Solo el admin ve la carga de los entrenadores — es la base para repartir el trabajo, no una vista del propio
 * entrenador.
 */
class ListCoachWorkloadAuthorizationTest :
    FunSpec({
        val club = TestClubs.newClub()

        fun principal(role: Role) = PrincipalBuilder().role(role).inClub(club).build()

        listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
            test("$role no puede listar, y no se toca la base") {
                val directory = InMemoryCoachDirectory()

                ListCoachWorkloadQuery(directory, mockk(relaxed = true))
                    .execute(principal(role))
                    .shouldBeLeft(ClubTaxonomiaError.Forbidden)

                directory.calls.size shouldBe 0
            }
        }

        test("el admin puede listar entrenadores") {
            ListCoachWorkloadQuery(InMemoryCoachDirectory(), mockk(relaxed = true))
                .execute(principal(Role.ADMIN))
                .shouldBeRight()
        }
    })
