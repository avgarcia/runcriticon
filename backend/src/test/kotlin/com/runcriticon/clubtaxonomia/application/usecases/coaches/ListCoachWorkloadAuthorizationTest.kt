package com.runcriticon.clubtaxonomia.application.usecases.coaches

import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

/**
 * Solo el admin ve la carga de los entrenadores — es la base para repartir el trabajo, no una vista del propio
 * entrenador.
 */
class ListCoachWorkloadAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
            test("$role no puede listar, y no se toca la base") {
                val directory = InMemoryCoachDirectory()

                ListCoachWorkloadQuery(directory)
                    .execute(principal(role))
                    .shouldBeLeft(ClubTaxonomiaError.Forbidden)

                directory.calls.size shouldBe 0
            }
        }

        test("el admin puede listar entrenadores") {
            ListCoachWorkloadQuery(InMemoryCoachDirectory())
                .execute(principal(Role.ADMIN))
                .shouldBeRight()
        }
    })
