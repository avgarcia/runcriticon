package com.runcriticon.clubtaxonomia.domain.taxonomy

import com.runcriticon.clubtaxonomia.domain.tag.ReservedRole
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyType
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

/**
 * Los valores de salud y el de lesión se reconocen por su marcador, nunca por su nombre: renombrarlos no los apaga, y
 * un valor que se llame «lesión» sin estar marcado no es dato de salud.
 */
class InjuryAxisTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val activo = valor("activo")
        val lesion = valor("lesión", healthData = true, role = ReservedRole.LESION)
        val postParto = valor("post-parto", healthData = true)
        val medio = valor("medio")

        test("localiza el valor con papel LESION y todos los valores de su eje") {
            val taxonomy =
                Taxonomy(
                    club,
                    listOf(clave(club, "nivel", listOf(medio)), clave(club, "estado", listOf(activo, lesion))),
                )

            taxonomy.injuryAxis() shouldBe
                InjuryAxis(axisValueIds = setOf(activo.id, lesion.id), injuryValueId = lesion.id)
        }

        test("renombrar el valor y el eje no cambia nada: se reconoce por el papel, no por el nombre") {
            val renombrado = lesion.copy(label = TagLabel.forValue("de baja").getOrNull()!!)
            val taxonomy = Taxonomy(club, listOf(clave(club, "situación", listOf(activo, renombrado))))

            taxonomy.injuryAxis()?.injuryValueId shouldBe lesion.id
            taxonomy.healthDataValueIds() shouldBe setOf(lesion.id)
        }

        test("un valor llamado lesion sin marcar no es el de lesion ni dato de salud") {
            val taxonomy = Taxonomy(club, listOf(clave(club, "estado", listOf(activo, valor("lesión")))))

            taxonomy.injuryAxis().shouldBeNull()
            taxonomy.healthDataValueIds() shouldBe emptySet()
        }

        test("los valores de salud incluyen los que no tienen papel y los archivados") {
            val archivado =
                valor(
                    "baja médica",
                    healthData = true,
                ).copy(archivedAt = Instant.parse("2026-07-01T10:00:00Z"))
            val taxonomy = Taxonomy(club, listOf(clave(club, "estado", listOf(activo, lesion, postParto, archivado))))

            taxonomy.healthDataValueIds() shouldBe setOf(lesion.id, postParto.id, archivado.id)
        }

        test("un valor con papel reservado que no es dato de salud es imposible") {
            shouldThrow<IllegalArgumentException> { valor("lesión", healthData = false, role = ReservedRole.LESION) }
        }
    })

private fun valor(
    label: String,
    healthData: Boolean = false,
    role: ReservedRole? = null,
) = TagValue(
    id = TagValueId.new(),
    label = TagLabel.forValue(label).getOrNull()!!,
    metadata = TagValueMetadata.Empty,
    archivedAt = null,
    healthData = healthData,
    reservedRole = role,
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
