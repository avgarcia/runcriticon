package com.runcriticon.seguimiento.application.usecases.clubhealth

import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

/** Solo el ADMIN ve la vista de salud del club; el rechazo no toca el lector. */
class ClubHealthAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        test("el ADMIN puede ver la actividad por grupo") {
            ListGroupActivityQuery(InMemoryClubHealthReader())
                .execute(principal(Role.ADMIN))
                .shouldBeRight()
        }

        listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
            test("$role no puede ver la vista de salud del club, y no se toca el lector") {
                val reader = InMemoryClubHealthReader()

                ListGroupActivityQuery(reader)
                    .execute(principal(role))
                    .shouldBeLeft(SeguimientoError.Forbidden)

                reader.calls.size shouldBe 0
            }
        }
    })
