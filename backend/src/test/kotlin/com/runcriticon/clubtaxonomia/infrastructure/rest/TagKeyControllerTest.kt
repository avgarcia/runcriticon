package com.runcriticon.clubtaxonomia.infrastructure.rest

import arrow.core.left
import arrow.core.right
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.AddTagValueCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ArchiveTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ChangeTagKeyTypeCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.CreateTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.GetTagKeyArchiveImpactQuery
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ReactivateTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.RenameTagKeyCommand
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupName
import com.runcriticon.clubtaxonomia.domain.tag.TagArchiveImpact
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyType
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.shared.api.rest.ErrorResponse
import com.runcriticon.shared.api.rest.ImpactoArchivadoResponse
import com.runcriticon.shared.api.rest.TagKeyCreateRequest
import com.runcriticon.shared.api.rest.TagKeyLabelRequest
import com.runcriticon.shared.api.rest.TagKeyResponse
import com.runcriticon.shared.api.rest.TagKeyTypeRequest
import com.runcriticon.shared.api.rest.TagValueCreateRequest
import com.runcriticon.shared.api.rest.TagValueMetadataRequest
import com.runcriticon.shared.api.rest.TagValueResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID
import com.runcriticon.shared.api.rest.TagKeyType as TagKeyTypeContract

/**
 * Test unitario de [TagKeyController]: mapeo `Either`→`ResponseEntity` sin contexto Spring. El enrutamiento real, el
 * CSRF y la conformidad con la spec se cubren en `TaxonomiaOpenApiContractTest`.
 */
class TagKeyControllerTest :
    FunSpec({
        val createTagKey = mockk<CreateTagKeyCommand>()
        val renameTagKey = mockk<RenameTagKeyCommand>()
        val archiveTagKey = mockk<ArchiveTagKeyCommand>()
        val reactivateTagKey = mockk<ReactivateTagKeyCommand>()
        val changeTagKeyType = mockk<ChangeTagKeyTypeCommand>()
        val addTagValue = mockk<AddTagValueCommand>()
        val getArchiveImpact = mockk<GetTagKeyArchiveImpactQuery>()
        val principalProvider = mockk<PrincipalProvider>()
        val controller =
            TagKeyController(
                createTagKey,
                renameTagKey,
                archiveTagKey,
                reactivateTagKey,
                changeTagKeyType,
                addTagValue,
                getArchiveImpact,
                principalProvider,
            )

        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ADMIN)
        val keyId = TagKeyId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val archivedAt = Instant.parse("2026-07-28T10:15:30Z")

        fun tagKey(archived: Instant? = null) =
            TagKey(
                id = keyId,
                clubId = clubId,
                label = TagLabel.forKey("Nivel").getOrNull()!!,
                type = TagKeyType.SIMPLE,
                archivedAt = archived,
                values = emptyList(),
            )

        beforeEach { every { principalProvider.current() } returns admin }

        test("create - 201 y el eje creado") {
            every { createTagKey.execute(any(), any(), any()) } returns tagKey().right()

            val resp = controller.create(TagKeyCreateRequest(nombre = "Nivel"))

            resp.statusCode shouldBe HttpStatus.CREATED
            (resp.body as TagKeyResponse).nombre shouldBe "Nivel"
            (resp.body as TagKeyResponse).archivadoEn shouldBe null
        }

        test("create - 409 con DUPLICATE_LABEL cuando el nombre ya existe") {
            every { createTagKey.execute(any(), any(), any()) } returns
                ClubTaxonomiaError.DuplicateLabel("nombre", "Nivel").left()

            val resp = controller.create(TagKeyCreateRequest(nombre = "Nivel"))

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "DUPLICATE_LABEL"
        }

        test("create - sin tipo explícito lo crea SIMPLE") {
            every { createTagKey.execute(any(), any(), TagKeyType.SIMPLE) } returns tagKey().right()

            val resp = controller.create(TagKeyCreateRequest(nombre = "Nivel"))

            resp.statusCode shouldBe HttpStatus.CREATED
        }

        test("create - tipo RACE se traduce al dominio") {
            every { createTagKey.execute(any(), any(), TagKeyType.RACE) } returns
                tagKey().copy(type = TagKeyType.RACE).right()

            val resp = controller.create(TagKeyCreateRequest(nombre = "Objetivo", tipo = TagKeyTypeContract.RACE))

            resp.statusCode shouldBe HttpStatus.CREATED
            (resp.body as TagKeyResponse).tipo shouldBe TagKeyTypeContract.RACE
        }

        test("changeType - 200 y el eje con el tipo cambiado") {
            every { changeTagKeyType.execute(any(), any(), TagKeyType.RACE) } returns
                tagKey().copy(type = TagKeyType.RACE).right()

            val resp = controller.changeType(keyId.value, TagKeyTypeRequest(tipo = TagKeyTypeContract.RACE))

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as TagKeyResponse).tipo shouldBe TagKeyTypeContract.RACE
        }

        test("changeType - 409 con TAG_KEY_HAS_RACE_VALUES al degradar un eje con carreras vivas") {
            every { changeTagKeyType.execute(any(), any(), TagKeyType.SIMPLE) } returns
                ClubTaxonomiaError.Conflict("tag_key_has_race_values").left()

            val resp = controller.changeType(keyId.value, TagKeyTypeRequest(tipo = TagKeyTypeContract.SIMPLE))

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "TAG_KEY_HAS_RACE_VALUES"
        }

        test("rename - 200 y el eje renombrado") {
            every { renameTagKey.execute(any(), any(), any()) } returns tagKey().right()

            val resp = controller.rename(keyId.value, TagKeyLabelRequest(nombre = "Nivel"))

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as TagKeyResponse).id shouldBe keyId.value
        }

        test("rename - 404 con TAG_KEY_NOT_FOUND") {
            every { renameTagKey.execute(any(), any(), any()) } returns ClubTaxonomiaError.TagKeyNotFound.left()

            val resp = controller.rename(keyId.value, TagKeyLabelRequest(nombre = "Nivel"))

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            (resp.body as ErrorResponse).code shouldBe "TAG_KEY_NOT_FOUND"
        }

        test("archive - 200 y el eje con su marca de archivado") {
            every { archiveTagKey.execute(any(), any()) } returns tagKey(archived = archivedAt).right()

            val resp = controller.archive(keyId.value)

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as TagKeyResponse).archivadoEn!!.toInstant() shouldBe archivedAt
        }

        test("archiveImpact - 200 con los alumnos afectados y los grupos que lo requieren") {
            val group =
                TagArchiveImpact.RequiringGroup(
                    groupId = GroupId.of(UUID.randomUUID()),
                    groupName = GroupName.of("5K").getOrNull()!!,
                    wouldLoseAllRequiredTags = true,
                )
            every { getArchiveImpact.execute(any(), any()) } returns
                TagArchiveImpact(studentsAffected = 3, groupsRequiring = listOf(group)).right()

            val resp = controller.archiveImpact(keyId.value)

            resp.statusCode shouldBe HttpStatus.OK
            val body = resp.body as ImpactoArchivadoResponse
            body.alumnosAfectados shouldBe 3
            body.gruposQueLoRequieren.single().perderiaTodosLosTagsRequeridos shouldBe true
        }

        test("archiveImpact - 404 con TAG_KEY_NOT_FOUND") {
            every { getArchiveImpact.execute(any(), any()) } returns ClubTaxonomiaError.TagKeyNotFound.left()

            val resp = controller.archiveImpact(keyId.value)

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            (resp.body as ErrorResponse).code shouldBe "TAG_KEY_NOT_FOUND"
        }

        test("archive - 409 con TAG_KEY_REQUIRED_BY_GROUP si un grupo vivo lo requiere") {
            every { archiveTagKey.execute(any(), any()) } returns
                ClubTaxonomiaError.TagKeyRequiredByGroup(setOf(GroupId.of(UUID.randomUUID()))).left()

            val resp = controller.archive(keyId.value)

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "TAG_KEY_REQUIRED_BY_GROUP"
        }

        test("reactivate - 200 y el eje sin marca de archivado") {
            every { reactivateTagKey.execute(any(), any()) } returns tagKey().right()

            val resp = controller.reactivate(keyId.value)

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as TagKeyResponse).archivadoEn shouldBe null
        }

        test("reactivate - 409 si el nombre se reocupó mientras estaba archivado") {
            every { reactivateTagKey.execute(any(), any()) } returns
                ClubTaxonomiaError.DuplicateLabel("nombre", "Nivel").left()

            val resp = controller.reactivate(keyId.value)

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "DUPLICATE_LABEL"
        }

        test("addValue - 201 y el valor creado con metadata vacía") {
            val value =
                TagValue(
                    id = TagValueId.of(UUID.randomUUID()),
                    label = TagLabel.forValue("Principiante").getOrNull()!!,
                    metadata = TagValueMetadata.Empty,
                    archivedAt = null,
                )
            every { addTagValue.execute(any(), any(), any(), any()) } returns value.right()

            val resp = controller.addValue(keyId.value, TagValueCreateRequest(valor = "Principiante"))

            resp.statusCode shouldBe HttpStatus.CREATED
            (resp.body as TagValueResponse).valor shouldBe "Principiante"
        }

        test("addValue - 409 con TAG_KEY_ARCHIVED si el eje está archivado") {
            every { addTagValue.execute(any(), any(), any(), any()) } returns
                ClubTaxonomiaError.Conflict("tag_key_archived").left()

            val resp = controller.addValue(keyId.value, TagValueCreateRequest(valor = "Principiante"))

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "TAG_KEY_ARCHIVED"
        }

        test("addValue - con metadata de carrera se desmonta en fecha y distancia") {
            val value =
                TagValue(
                    id = TagValueId.of(UUID.randomUUID()),
                    label = TagLabel.forValue("Maratón").getOrNull()!!,
                    metadata = TagValueMetadata.Empty,
                    archivedAt = null,
                )
            every {
                addTagValue.execute(
                    any(),
                    any(),
                    any(),
                    match { it.date == java.time.LocalDate.of(2026, 12, 6) && it.distance == "42K" },
                )
            } returns value.right()

            val resp =
                controller.addValue(
                    keyId.value,
                    TagValueCreateRequest(
                        valor = "Maratón",
                        metadata =
                            TagValueMetadataRequest(
                                tipo = TagValueMetadataRequest.Tipo.RACE,
                                fecha = java.time.LocalDate.of(2026, 12, 6),
                                distancia = TagValueMetadataRequest.Distancia._42_K,
                            ),
                    ),
                )

            resp.statusCode shouldBe HttpStatus.CREATED
        }

        test("un rol sin permiso se traduce a 403 neutro") {
            every { createTagKey.execute(any(), any(), any()) } returns ClubTaxonomiaError.Forbidden.left()

            val resp = controller.create(TagKeyCreateRequest(nombre = "Nivel"))

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            (resp.body as ErrorResponse).code shouldBe "FORBIDDEN"
        }
    })
