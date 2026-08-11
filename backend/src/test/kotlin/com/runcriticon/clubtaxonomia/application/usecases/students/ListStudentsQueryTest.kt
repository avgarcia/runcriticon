package com.runcriticon.clubtaxonomia.application.usecases.students

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ListStudentsQueryTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val tag = TagValueId.of(UuidCreator.getTimeOrderedEpoch())

        val alumnos =
            listOf(
                StudentSummary(
                    id = PersonId.of(UuidCreator.getTimeOrderedEpoch()),
                    name = "Pedro Cordero",
                    email = "pedro@club.test",
                    status = PersonStatus.ACTIVO,
                    tagValueIds = setOf(tag),
                ),
            )

        test("devuelve lo que resuelve el repositorio") {
            val directory = InMemoryStudentDirectory(alumnos)

            ListStudentsQuery(directory).execute(admin, listOf(tag.value)).shouldBeRight() shouldBe alumnos
        }

        test("opera sobre el club del actor y el filtro pedido") {
            val directory = InMemoryStudentDirectory()

            ListStudentsQuery(directory).execute(admin, listOf(tag.value))

            directory.calls.single() shouldBe (club to setOf(tag))
        }

        test("sin tagValueId pasa un filtro vacio") {
            val directory = InMemoryStudentDirectory()

            ListStudentsQuery(directory).execute(admin, emptyList())

            directory.calls.single().second shouldBe emptySet()
        }
    })
