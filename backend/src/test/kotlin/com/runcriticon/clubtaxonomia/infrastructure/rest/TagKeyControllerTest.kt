package com.runcriticon.clubtaxonomia.infrastructure.rest

import arrow.core.left
import arrow.core.right
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.AddTagValueCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ArchiveTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.CreateTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ReactivateTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.RenameTagKeyCommand
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.shared.api.rest.ErrorResponse
import com.runcriticon.shared.api.rest.TagKeyLabelRequest
import com.runcriticon.shared.api.rest.TagKeyResponse
import com.runcriticon.shared.api.rest.TagValueLabelRequest
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
        val addTagValue = mockk<AddTagValueCommand>()
        val principalProvider = mockk<PrincipalProvider>()
        val controller =
            TagKeyController(
                createTagKey,
                renameTagKey,
                archiveTagKey,
                reactivateTagKey,
                addTagValue,
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
                archivedAt = archived,
                values = emptyList(),
            )

        beforeEach { every { principalProvider.current() } returns admin }

        test("create - 201 y el eje creado") {
            every { createTagKey.execute(any(), any()) } returns tagKey().right()

            val resp = controller.create(TagKeyLabelRequest(nombre = "Nivel"))

            resp.statusCode shouldBe HttpStatus.CREATED
            (resp.body as TagKeyResponse).nombre shouldBe "Nivel"
            (resp.body as TagKeyResponse).archivadoEn shouldBe null
        }

        test("create - 409 con DUPLICATE_LABEL cuando el nombre ya existe") {
            every { createTagKey.execute(any(), any()) } returns
                ClubTaxonomiaError.DuplicateLabel("nombre", "Nivel").left()

            val resp = controller.create(TagKeyLabelRequest(nombre = "Nivel"))

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "DUPLICATE_LABEL"
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
            every { addTagValue.execute(any(), any(), any()) } returns value.right()

            val resp = controller.addValue(keyId.value, TagValueLabelRequest(valor = "Principiante"))

            resp.statusCode shouldBe HttpStatus.CREATED
            (resp.body as TagValueResponse).valor shouldBe "Principiante"
        }

        test("addValue - 409 con TAG_KEY_ARCHIVED si el eje está archivado") {
            every { addTagValue.execute(any(), any(), any()) } returns
                ClubTaxonomiaError.Conflict("tag_key_archived").left()

            val resp = controller.addValue(keyId.value, TagValueLabelRequest(valor = "Principiante"))

            resp.statusCode shouldBe HttpStatus.CONFLICT
            (resp.body as ErrorResponse).code shouldBe "TAG_KEY_ARCHIVED"
        }

        test("un rol sin permiso se traduce a 403 neutro") {
            every { createTagKey.execute(any(), any()) } returns ClubTaxonomiaError.Forbidden.left()

            val resp = controller.create(TagKeyLabelRequest(nombre = "Nivel"))

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            (resp.body as ErrorResponse).code shouldBe "FORBIDDEN"
        }
    })
