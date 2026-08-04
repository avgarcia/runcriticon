package com.runcriticon.clubtaxonomia.domain.studenttags

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagLabel
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * La composición de la clasificación de un alumno, sin base de datos: qué entra, en qué orden y qué se ignora.
 */
class StudentTagsTest :
    FunSpec({
        val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val student = PersonId.of(UuidCreator.getTimeOrderedEpoch())

        val nivelMedio = valor("medio")
        val nivelAlto = valor("alto")
        val maratonValencia = valor("maratón valencia")
        val mediaMadrid = valor("media madrid")
        val montana = valor("montaña", archived = true)

        val nivel = clave("nivel", listOf(nivelMedio, nivelAlto))
        val objetivo = clave("objetivo", listOf(maratonValencia, mediaMadrid))
        val terreno = clave("terreno", listOf(montana))
        val taxonomy = Taxonomy.rehydrate(club, listOf(nivel, objetivo, terreno))

        test("el orden es el de la taxonomia, no el de los ids asignados") {
            val ids = setOf(mediaMadrid.id, nivelMedio.id, maratonValencia.id)

            val classified = StudentTags.of(student, taxonomy, ids)

            classified.assigned.map { it.value.label.value } shouldBe
                listOf("medio", "maratón valencia", "media madrid")
        }

        test("un alumno puede llevar dos valores del mismo eje") {
            val classified = StudentTags.of(student, taxonomy, setOf(maratonValencia.id, mediaMadrid.id))

            classified.assigned shouldHaveSize 2
            classified.assigned.map { it.keyId } shouldBe listOf(objetivo.id, objetivo.id)
        }

        test("cada valor viaja con el eje del que cuelga") {
            val classified = StudentTags.of(student, taxonomy, setOf(nivelAlto.id, maratonValencia.id))

            classified.assigned.map { it.keyId } shouldBe listOf(nivel.id, objetivo.id)
        }

        test("un valor archivado que el alumno ya tenia sigue apareciendo") {
            val classified = StudentTags.of(student, taxonomy, setOf(montana.id))

            classified.assigned
                .single()
                .value.id shouldBe montana.id
        }

        test("un alumno sin clasificar da una lista vacia") {
            StudentTags.of(student, taxonomy, emptySet()).assigned.shouldBeEmpty()
        }

        test("un id que ya no esta en la taxonomia se ignora sin romper el resto") {
            val desaparecido = TagValueId.of(UuidCreator.getTimeOrderedEpoch())

            val classified = StudentTags.of(student, taxonomy, setOf(nivelMedio.id, desaparecido))

            classified.assigned
                .single()
                .value.id shouldBe nivelMedio.id
        }

        test("conserva el alumno al que pertenece la clasificacion") {
            StudentTags.of(student, taxonomy, emptySet()).studentId shouldBe student
        }
    })

private fun valor(
    label: String,
    archived: Boolean = false,
) = TagValue(
    id = TagValueId.new(),
    label = TagLabel.forValue(label).getOrNull()!!,
    metadata = TagValueMetadata.Empty,
    archivedAt = if (archived) Instant.parse("2026-07-01T10:00:00Z") else null,
)

private fun clave(
    label: String,
    values: List<TagValue>,
) = TagKey(
    id = TagKeyId.new(),
    clubId = ClubId.of(UuidCreator.getTimeOrderedEpoch()),
    label = TagLabel.forKey(label).getOrNull()!!,
    archivedAt = null,
    values = values,
)
