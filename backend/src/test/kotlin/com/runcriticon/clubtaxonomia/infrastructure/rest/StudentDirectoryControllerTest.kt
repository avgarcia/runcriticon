package com.runcriticon.clubtaxonomia.infrastructure.rest

import arrow.core.left
import arrow.core.right
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.usecases.students.ListStudentsQuery
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.api.rest.ErrorResponse
import com.runcriticon.shared.api.rest.StudentSummaryResponse
import com.runcriticon.shared.api.rest.StudentsResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * Test unitario de [StudentDirectoryController]: mapeo `Either`→`ResponseEntity` sin contexto Spring. El enrutamiento
 * real, el CSRF y la conformidad con la spec se cubren en `AlumnosOpenApiContractTest`.
 */
class StudentDirectoryControllerTest :
    FunSpec({
        val listStudents = mockk<ListStudentsQuery>()
        val principalProvider = mockk<PrincipalProvider>()
        val controller = StudentDirectoryController(listStudents, principalProvider)

        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)
        val tagId = TagValueId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"))

        beforeEach { every { principalProvider.current() } returns admin }

        test("list - 200 con los alumnos y sus tags") {
            val alumno =
                StudentSummary(
                    id = PersonId.of(UuidCreator.getTimeOrderedEpoch()),
                    name = "Pedro Cordero",
                    email = "pedro@club.test",
                    status = PersonStatus.ACTIVO,
                    tagValueIds = setOf(tagId),
                )
            every { listStudents.execute(any(), any()) } returns listOf(alumno).right()

            val resp = controller.list(listOf(tagId.value))

            resp.statusCode shouldBe HttpStatus.OK
            val body = resp.body as StudentsResponse
            body.alumnos.single().nombre shouldBe "Pedro Cordero"
            body.alumnos.single().estado shouldBe StudentSummaryResponse.Estado.ACTIVO
            body.alumnos.single().valores shouldBe listOf(tagId.value)
        }

        test("list - 200 con lista vacia si nadie cumple el filtro") {
            every { listStudents.execute(any(), any()) } returns emptyList<StudentSummary>().right()

            val resp = controller.list(listOf(tagId.value))

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as StudentsResponse).alumnos shouldBe emptyList()
        }

        // Sin ningún `tagValueId` en la URL, Spring inyecta null: el controlador tiene que traducirlo a filtro vacío.
        test("list - sin parametros pasa un filtro vacio al caso de uso") {
            val filtro = slot<List<UUID>>()
            every { listStudents.execute(any(), capture(filtro)) } returns emptyList<StudentSummary>().right()

            val resp = controller.list(null)

            resp.statusCode shouldBe HttpStatus.OK
            filtro.captured shouldBe emptyList()
        }

        test("list - 403 si el rol no puede") {
            every { listStudents.execute(any(), any()) } returns ClubTaxonomiaError.Forbidden.left()

            val resp = controller.list(null)

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            (resp.body as ErrorResponse).code shouldBe "FORBIDDEN"
        }
    })
