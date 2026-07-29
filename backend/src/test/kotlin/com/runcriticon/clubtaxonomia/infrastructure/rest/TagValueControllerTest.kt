package com.runcriticon.clubtaxonomia.infrastructure.rest

import arrow.core.left
import arrow.core.right
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ArchiveTagValueCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ReactivateTagValueCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.RenameTagValueCommand
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.Distance
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.shared.api.rest.ErrorResponse
import com.runcriticon.shared.api.rest.RaceMetadata
import com.runcriticon.shared.api.rest.TagValueLabelRequest
import com.runcriticon.shared.api.rest.TagValueResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Test unitario de [TagValueController], incluida la traducción de la metadata de carrera al contrato. */
class TagValueControllerTest :
    FunSpec({
        val renameTagValue = mockk<RenameTagValueCommand>()
        val archiveTagValue = mockk<ArchiveTagValueCommand>()
        val reactivateTagValue = mockk<ReactivateTagValueCommand>()
        val principalProvider = mockk<PrincipalProvider>()
        val controller = TagValueController(renameTagValue, archiveTagValue, reactivateTagValue, principalProvider)

        val admin =
            Principal(
                userId = UUID.randomUUID(),
                clubId = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                role = Role.ADMIN,
            )
        val valueId = TagValueId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"))

        fun tagValue(
            archived: Instant? = null,
            metadata: TagValueMetadata = TagValueMetadata.Empty,
        ) = TagValue(
            id = valueId,
            label = TagLabel.forValue("5K").getOrNull()!!,
            metadata = metadata,
            archivedAt = archived,
        )

        beforeEach { every { principalProvider.current() } returns admin }

        test("rename - 200 y el valor renombrado") {
            every { renameTagValue.execute(any(), any(), any()) } returns tagValue().right()

            val resp = controller.rename(valueId.value, TagValueLabelRequest(valor = "5K"))

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as TagValueResponse).valor shouldBe "5K"
        }

        test("rename - 404 con TAG_VALUE_NOT_FOUND") {
            every { renameTagValue.execute(any(), any(), any()) } returns ClubTaxonomiaError.TagValueNotFound.left()

            val resp = controller.rename(valueId.value, TagValueLabelRequest(valor = "5K"))

            resp.statusCode shouldBe HttpStatus.NOT_FOUND
            (resp.body as ErrorResponse).code shouldBe "TAG_VALUE_NOT_FOUND"
        }

        test("rename - 400 con LABEL_BLANK cuando el literal viene vacío") {
            every { renameTagValue.execute(any(), any(), any()) } returns
                ClubTaxonomiaError.InvalidInput("valor", "blank").left()

            val resp = controller.rename(valueId.value, TagValueLabelRequest(valor = "  "))

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            (resp.body as ErrorResponse).code shouldBe "LABEL_BLANK"
            (resp.body as ErrorResponse).field shouldBe "valor"
        }

        test("archive - 200 con la marca de archivado") {
            val archivedAt = Instant.parse("2026-07-28T10:15:30Z")
            every { archiveTagValue.execute(any(), any()) } returns tagValue(archived = archivedAt).right()

            val resp = controller.archive(valueId.value)

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as TagValueResponse).archivadoEn!!.toInstant() shouldBe archivedAt
        }

        test("reactivate - 200 sin marca de archivado") {
            every { reactivateTagValue.execute(any(), any()) } returns tagValue().right()

            val resp = controller.reactivate(valueId.value)

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as TagValueResponse).archivadoEn shouldBe null
        }

        test(
            "la metadata de carrera viaja con el código de negocio de la distancia, no con el nombre de la constante",
        ) {
            val race = TagValueMetadata.Race(date = LocalDate.of(2026, 12, 6), distance = Distance.K42)
            every { renameTagValue.execute(any(), any(), any()) } returns tagValue(metadata = race).right()

            val resp = controller.rename(valueId.value, TagValueLabelRequest(valor = "Maratón de Valencia"))

            val metadata = (resp.body as TagValueResponse).metadata as RaceMetadata
            metadata.tipo shouldBe RaceMetadata.Tipo.RACE
            metadata.distancia.value shouldBe "42K"
            metadata.fecha shouldBe LocalDate.of(2026, 12, 6)
        }
    })
