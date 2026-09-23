package com.runcriticon.clubtaxonomia.application.usecases.students

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
 * El listado de alumnos lo consultan el admin y el entrenador; el alumno queda fuera y el rechazo no toca la base.
 */
class ListStudentsAuthorizationTest :
    FunSpec({
        val club = TestClubs.newClub()

        fun principal(role: Role) = PrincipalBuilder().role(role).inClub(club).build()

        test("el alumno no puede listar, y no se toca la base") {
            val directory = InMemoryStudentDirectory()

            ListStudentsQuery(directory, mockk(relaxed = true))
                .execute(principal(Role.ALUMNO), emptyList())
                .shouldBeLeft(ClubTaxonomiaError.Forbidden)

            directory.calls.size shouldBe 0
        }

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role puede listar alumnos") {
                ListStudentsQuery(InMemoryStudentDirectory(), mockk(relaxed = true))
                    .execute(principal(role), emptyList())
                    .shouldBeRight()
            }
        }
    })
