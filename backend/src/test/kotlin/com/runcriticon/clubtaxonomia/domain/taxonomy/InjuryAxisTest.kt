package com.runcriticon.clubtaxonomia.domain.taxonomy

import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyType
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class InjuryAxisTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val activo = valor("activo")
        val lesion = valor("Lesión")
        val medio = valor("medio")

        test("localiza el eje estado y su valor lesion ignorando mayusculas y tildes") {
            val taxonomy =
                Taxonomy(
                    club,
                    listOf(clave(club, "nivel", listOf(medio)), clave(club, "Estado", listOf(activo, lesion))),
                )

            taxonomy.injuryAxis() shouldBe
                InjuryAxis(axisValueIds = setOf(activo.id, lesion.id), injuryValueId = lesion.id)
        }

        test("sin eje estado no hay tag de lesion") {
            Taxonomy(club, listOf(clave(club, "nivel", listOf(medio)))).injuryAxis().shouldBeNull()
        }

        test("con eje estado pero sin valor lesion (renombrado) tampoco") {
            Taxonomy(
                club,
                listOf(clave(club, "estado", listOf(activo, valor("baja médica")))),
            ).injuryAxis().shouldBeNull()
        }
    })

private fun valor(label: String) =
    TagValue(
        id = TagValueId.new(),
        label = TagLabel.forValue(label).getOrNull()!!,
        metadata = TagValueMetadata.Empty,
        archivedAt = null,
    )

private fun clave(
    club: ClubId,
    label: String,
    values: List<TagValue>,
) = TagKey(
    id = TagKeyId.new(),
    clubId = club,
    label = TagLabel.forKey(label).getOrNull()!!,
    type = TagKeyType.SIMPLE,
    archivedAt = null,
    values = values,
)
