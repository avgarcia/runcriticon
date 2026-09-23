package com.runcriticon.seguimiento.application.usecases.clubhealth

import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.testing.PrincipalBuilder
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Solo el ADMIN ve la vista de salud del club; el rechazo no toca el lector. */
class ClubHealthAuthorizationTest :
    FunSpec({
        fun principal(role: Role) = PrincipalBuilder().role(role).build()

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
