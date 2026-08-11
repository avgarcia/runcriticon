package com.runcriticon.clubtaxonomia.infrastructure.rest

import arrow.core.left
import arrow.core.right
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.usecases.groups.ClearGroupMembershipOverrideCommand
import com.runcriticon.clubtaxonomia.application.usecases.groups.CreateGroupCommand
import com.runcriticon.clubtaxonomia.application.usecases.groups.GetGroupDetailQuery
import com.runcriticon.clubtaxonomia.application.usecases.groups.ListGroupsQuery
import com.runcriticon.clubtaxonomia.application.usecases.groups.OverrideGroupMembershipCommand
import com.runcriticon.clubtaxonomia.application.usecases.groups.PreviewGroupMembersQuery
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.group.GroupExclusion
import com.runcriticon.clubtaxonomia.domain.group.GroupMember
import com.runcriticon.clubtaxonomia.domain.group.GroupMemberOrigin
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.group.GroupMembership
import com.runcriticon.clubtaxonomia.domain.group.GroupSummary
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.api.rest.CreateGroupRequest
import com.runcriticon.shared.api.rest.ErrorResponse
import com.runcriticon.shared.api.rest.GroupDetailResponse
import com.runcriticon.shared.api.rest.GroupMembersResponse
import com.runcriticon.shared.api.rest.GroupOverrideRequest
import com.runcriticon.shared.api.rest.GroupResponse
import com.runcriticon.shared.api.rest.GroupsResponse
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
import com.runcriticon.shared.api.rest.GroupMemberOrigin as ApiGroupMemberOrigin

/**
 * Test unitario de [GroupController]: mapeo `Either`→`ResponseEntity` sin contexto Spring. El enrutamiento real, el
 * CSRF y la conformidad con la spec se cubren en `GruposOpenApiContractTest`.
 */
class GroupControllerTest :
    FunSpec({
        val listGroups = mockk<ListGroupsQuery>()
        val createGroup = mockk<CreateGroupCommand>()
        val previewGroupMembers = mockk<PreviewGroupMembersQuery>()
        val getGroupDetail = mockk<GetGroupDetailQuery>()
        val overrideMembership = mockk<OverrideGroupMembershipCommand>()
        val clearOverride = mockk<ClearGroupMembershipOverrideCommand>()
        val principalProvider = mockk<PrincipalProvider>()
        val controller =
            GroupController(
                listGroups,
                createGroup,
                previewGroupMembers,
                getGroupDetail,
                overrideMembership,
                clearOverride,
                principalProvider,
            )

        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)
        val valueId = TagValueId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"))

        val group = Group.create(clubId, "Maratón Valencia", setOf(valueId)).getOrNull()!!

        beforeEach { every { principalProvider.current() } returns admin }

        test("list - 200 con los grupos y su recuento de alumnos") {
            every { listGroups.execute(any()) } returns listOf(GroupSummary(group, memberCount = 12)).right()

            val resp = controller.list()

            resp.statusCode shouldBe HttpStatus.OK
            val body = resp.body as GroupsResponse
            body.grupos.single().nombre shouldBe "Maratón Valencia"
            body.grupos.single().totalAlumnos shouldBe 12
            body.grupos.single().valores shouldBe listOf(valueId.value)
        }

        test("list - 200 con lista vacia si el club no tiene grupos") {
            every { listGroups.execute(any()) } returns emptyList<GroupSummary>().right()

            val resp = controller.list()

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as GroupsResponse).grupos shouldBe emptyList()
        }

        test("list - 403 si el rol no puede") {
            every { listGroups.execute(any()) } returns ClubTaxonomiaError.Forbidden.left()

            val resp = controller.list()

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            (resp.body as ErrorResponse).code shouldBe "FORBIDDEN"
        }

        test("create - 201 con el grupo y su filtro") {
            every { createGroup.execute(any(), any(), any()) } returns group.right()

            val resp =
                controller.create(CreateGroupRequest(nombre = "Maratón Valencia", valores = listOf(valueId.value)))

            resp.statusCode shouldBe HttpStatus.CREATED
            (resp.body as GroupResponse).nombre shouldBe "Maratón Valencia"
            (resp.body as GroupResponse).valores shouldBe listOf(valueId.value)
        }

        test("create - 404 si el valor no existe") {
            every { createGroup.execute(any(), any(), any()) } returns ClubTaxonomiaError.TagValueNotFound.left()

            val resp = controller.create(CreateGroupRequest(nombre = "Fantasma", valores = listOf(valueId.value)))

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            (resp.body as ErrorResponse).code shouldBe "TAG_VALUE_NOT_FOUND"
        }

        test("create - 409 si el valor esta archivado") {
            every { createGroup.execute(any(), any(), any()) } returns
                ClubTaxonomiaError.Conflict("tag_value_not_assignable").left()

            val resp = controller.create(CreateGroupRequest(nombre = "Montañeros", valores = listOf(valueId.value)))

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "TAG_VALUE_NOT_ASSIGNABLE"
        }

        test("create - 400 con el campo del nombre si viene en blanco") {
            every { createGroup.execute(any(), any(), any()) } returns
                ClubTaxonomiaError.InvalidInput("nombre", "blank").left()

            val resp = controller.create(CreateGroupRequest(nombre = " ", valores = emptyList()))

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            (resp.body as ErrorResponse).code shouldBe "LABEL_BLANK"
            (resp.body as ErrorResponse).field shouldBe "nombre"
        }

        test("create - 403 si el rol no puede") {
            every { createGroup.execute(any(), any(), any()) } returns ClubTaxonomiaError.Forbidden.left()

            val resp = controller.create(CreateGroupRequest(nombre = "Prohibido", valores = emptyList()))

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        test("previewMembers - 200 con el total y los alumnos") {
            val alumno = PersonId.of(UuidCreator.getTimeOrderedEpoch())
            every { previewGroupMembers.execute(any(), any()) } returns
                GroupMembers(listOf(GroupMember(alumno, "Pedro Cordero"))).right()

            val resp = controller.previewMembers(listOf(valueId.value))

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as GroupMembersResponse).total shouldBe 1
            (resp.body as GroupMembersResponse).alumnos.single().nombre shouldBe "Pedro Cordero"
        }

        // Sin ningún `tagValueId` en la URL, Spring inyecta null: el controlador tiene que traducirlo a filtro vacío,
        // no dejar que llegue como nulo al caso de uso.
        test("previewMembers - sin parametros pasa un filtro vacio al caso de uso") {
            val filtro = slot<List<UUID>>()
            every { previewGroupMembers.execute(any(), capture(filtro)) } returns GroupMembers.Empty.right()

            val resp = controller.previewMembers(null)

            resp.statusCode shouldBe HttpStatus.OK
            filtro.captured shouldBe emptyList()
            (resp.body as GroupMembersResponse).total shouldBe 0
        }

        test("previewMembers - 409 si el filtro trae un valor archivado") {
            every { previewGroupMembers.execute(any(), any()) } returns
                ClubTaxonomiaError.Conflict("tag_value_not_assignable").left()

            val resp = controller.previewMembers(listOf(valueId.value))

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "TAG_VALUE_NOT_ASSIGNABLE"
        }

        val alumno = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        val excluido = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        val detalle =
            GroupDetail(
                group = group,
                members =
                    listOf(
                        GroupMembership(
                            GroupMember(alumno, "Pedro Cordero"),
                            GroupMemberOrigin.MANUAL_INCLUSION,
                            hasOverride = true,
                        ),
                    ),
                exclusions = listOf(GroupExclusion(GroupMember(excluido, "Marta Lois"), matchesFilter = true)),
            )

        test("detail - 200 con los miembros, su origen y los excluidos") {
            every { getGroupDetail.execute(any(), any()) } returns detalle.right()

            val resp = controller.detail(group.id.value)

            resp.statusCode shouldBe HttpStatus.OK
            val body = resp.body as GroupDetailResponse
            body.total shouldBe 1
            body.miembros.single().nombre shouldBe "Pedro Cordero"
            body.miembros.single().origen shouldBe ApiGroupMemberOrigin.INCLUSION_MANUAL
            body.miembros.single().ajusteManual shouldBe true
            body.excluidos.single().cumpleFiltro shouldBe true
        }

        test("detail - 404 si el grupo no existe o es de otro club") {
            every { getGroupDetail.execute(any(), any()) } returns ClubTaxonomiaError.GroupNotFound.left()

            val resp = controller.detail(UUID.randomUUID())

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            (resp.body as ErrorResponse).code shouldBe "GROUP_NOT_FOUND"
        }

        test("setOverride - 200 con el detalle recalculado") {
            val incluido = slot<Boolean>()
            every { overrideMembership.execute(any(), any(), any(), capture(incluido)) } returns detalle.right()

            val resp = controller.setOverride(group.id.value, alumno.value, GroupOverrideRequest(incluido = true))

            resp.statusCode shouldBe HttpStatus.OK
            incluido.captured shouldBe true
            (resp.body as GroupDetailResponse).miembros.single().ajusteManual shouldBe true
        }

        test("setOverride - 404 si la persona no es alumno del club") {
            every { overrideMembership.execute(any(), any(), any(), any()) } returns
                ClubTaxonomiaError.StudentNotFound.left()

            val resp = controller.setOverride(group.id.value, alumno.value, GroupOverrideRequest(incluido = false))

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            (resp.body as ErrorResponse).code shouldBe "STUDENT_NOT_FOUND"
        }

        test("setOverride - 403 si el rol no puede") {
            every { overrideMembership.execute(any(), any(), any(), any()) } returns ClubTaxonomiaError.Forbidden.left()

            val resp = controller.setOverride(group.id.value, alumno.value, GroupOverrideRequest(incluido = true))

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
        }

        test("clearOverride - 204 sin cuerpo, hubiera excepcion o no") {
            every { clearOverride.execute(any(), any(), any()) } returns Unit.right()

            val resp = controller.clearOverride(group.id.value, alumno.value)

            resp.statusCode shouldBe HttpStatus.NO_CONTENT
            resp.body shouldBe null
        }

        test("clearOverride - 404 si el grupo no es del club") {
            every { clearOverride.execute(any(), any(), any()) } returns ClubTaxonomiaError.GroupNotFound.left()

            val resp = controller.clearOverride(UUID.randomUUID(), alumno.value)

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            (resp.body as ErrorResponse).code shouldBe "GROUP_NOT_FOUND"
        }
    })
