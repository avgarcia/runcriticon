package com.runcriticon.clubtaxonomia.application.usecases.students

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
 * El listado de alumnos lo consultan el admin y el entrenador; el alumno queda fuera y el rechazo no toca la base.
 */
class ListStudentsAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        test("el alumno no puede listar, y no se toca la base") {
            val directory = InMemoryStudentDirectory()

            ListStudentsQuery(directory)
                .execute(principal(Role.ALUMNO), emptyList())
                .shouldBeLeft(ClubTaxonomiaError.Forbidden)

            directory.calls.size shouldBe 0
        }

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role puede listar alumnos") {
                ListStudentsQuery(InMemoryStudentDirectory())
                    .execute(principal(role), emptyList())
                    .shouldBeRight()
            }
        }
    })
