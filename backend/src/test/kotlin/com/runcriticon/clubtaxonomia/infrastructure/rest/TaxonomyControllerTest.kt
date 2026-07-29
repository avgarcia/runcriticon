package com.runcriticon.clubtaxonomia.infrastructure.rest

import arrow.core.left
import arrow.core.right
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ListTaxonomyQuery
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.api.rest.EmptyMetadata
import com.runcriticon.shared.api.rest.ErrorResponse
import com.runcriticon.shared.api.rest.TaxonomyResponse
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

/** Test unitario de [TaxonomyController]: el árbol de dominio se aplana al contrato con los archivados incluidos. */
class TaxonomyControllerTest :
    FunSpec({
        val listTaxonomy = mockk<ListTaxonomyQuery>()
        val principalProvider = mockk<PrincipalProvider>()
        val controller = TaxonomyController(listTaxonomy, principalProvider)

        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val coach = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = Role.ENTRENADOR)

        beforeEach { every { principalProvider.current() } returns coach }

        test("get - 200 con los ejes y sus valores, archivados incluidos") {
            val value =
                TagValue(
                    id = TagValueId.of(UUID.randomUUID()),
                    label = TagLabel.forValue("Principiante").getOrNull()!!,
                    metadata = TagValueMetadata.Empty,
                    archivedAt = null,
                )
            val activa =
                TagKey(
                    id = TagKeyId.of(UUID.randomUUID()),
                    clubId = clubId,
                    label = TagLabel.forKey("Nivel").getOrNull()!!,
                    archivedAt = null,
                    values = listOf(value),
                )
            val archivada =
                TagKey(
                    id = TagKeyId.of(UUID.randomUUID()),
                    clubId = clubId,
                    label = TagLabel.forKey("Terreno").getOrNull()!!,
                    archivedAt = Instant.parse("2026-07-28T10:15:30Z"),
                    values = emptyList(),
                )
            every { listTaxonomy.execute(any()) } returns Taxonomy.rehydrate(clubId, listOf(activa, archivada)).right()

            val resp = controller.get()

            resp.statusCode shouldBe HttpStatus.OK
            val body = resp.body as TaxonomyResponse
            body.tags.map { it.nombre } shouldBe listOf("Nivel", "Terreno")
            body.tags[0]
                .valores
                .single()
                .metadata shouldBe EmptyMetadata(EmptyMetadata.Tipo.EMPTY)
            body.tags[0].archivadoEn shouldBe null
            body.tags[1].archivadoEn!!.toInstant() shouldBe Instant.parse("2026-07-28T10:15:30Z")
        }

        test("get - 200 con la taxonomía vacía de un club nuevo") {
            every { listTaxonomy.execute(any()) } returns Taxonomy.empty(clubId).right()

            val resp = controller.get()

            resp.statusCode shouldBe HttpStatus.OK
            (resp.body as TaxonomyResponse).tags shouldBe emptyList()
        }

        test("get - 403 cuando la matriz deniega (el alumno no consulta la taxonomía)") {
            every { listTaxonomy.execute(any()) } returns ClubTaxonomiaError.Forbidden.left()

            val resp = controller.get()

            resp.statusCode shouldBe HttpStatus.FORBIDDEN
            (resp.body as ErrorResponse).code shouldBe "FORBIDDEN"
        }
    })
