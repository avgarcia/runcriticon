package com.runcriticon.clubtaxonomia.infrastructure.rest

import arrow.core.left
import arrow.core.right
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.usecases.coaches.ListCoachWorkloadQuery
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.CoachWorkload
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.person.PersonStatus
import com.runcriticon.shared.api.rest.CoachWorkloadResponse
import com.runcriticon.shared.api.rest.CoachWorkloadsResponse
import com.runcriticon.shared.api.rest.ErrorResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * Test unitario de [CoachDirectoryController]: mapeo `Either`→`ResponseEntity` sin contexto Spring. El enrutamiento
 * real y la conformidad con la spec se cubren en el contrato OpenAPI.
 */
class CoachDirectoryControllerTest :
    FunSpec({
        val listCoachWorkload = mockk<ListCoachWorkloadQuery>()
        val principalProvider = mockk<PrincipalProvider>()
        val controller = CoachDirectoryController(listCoachWorkload, principalProvider)

        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)

        beforeEach { every { principalProvider.current() } returns admin }

        test("list - 200 con los entrenadores y su carga") {
            val entrenador =
                CoachWorkload(
                    id = PersonId.of(UuidCreator.getTimeOrderedEpoch()),
                    name = "Carlos Ruiz",
                    email = "carlos@club.test",
                    status = PersonStatus.ACTIVO,
                    groups = emptyList(),
                    totalStudents = 0,
                )
            every { listCoachWorkload.execute(any()) } returns listOf(entrenador).right()

            val resp = controller.list()

            resp.statusCode shouldBe HttpStatus.OK
            val body = resp.body as CoachWorkloadsResponse
            body.entrenadores.single().nombre shouldBe "Carlos Ruiz"
            body.entrenadores.single().estado shouldBe CoachWorkloadResponse.Estado.ACTIVO
            body.entrenadores.single().grupos shouldBe emptyList()
        }

        test("list - 200 con lista vacia si el club no tiene entrenadores") {
            every { listCoachWorkload.execute(any()) } returns emptyList<CoachWorkload>().right()

            val resp = controller.list()

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as CoachWorkloadsResponse).entrenadores shouldBe emptyList()
        }

        test("list - 403 si el rol no puede") {
            every { listCoachWorkload.execute(any()) } returns ClubTaxonomiaError.Forbidden.left()

            val resp = controller.list()

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            (resp.body as ErrorResponse).code shouldBe "FORBIDDEN"
        }
    })
