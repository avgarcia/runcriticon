package com.runcriticon.clubtaxonomia.domain.taxonomy

import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.Distance
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class TaxonomyTest :
    FunSpec({
        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val at = Instant.parse("2026-07-22T10:00:00Z")

        // --- unicidad -------------------------------------------------------------------------------------------

        test("addKey rechaza un nombre duplicado ignorando mayúsculas y espacios (devuelve Left, no excepción)") {
            val base =
                Taxonomy
                    .empty(clubId)
                    .addKey("Nivel")
                    .shouldBeRight()
                    .taxonomy
            base.addKey("nivel ").shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("nombre", "nivel"))
            base.activeKeys().size shouldBe 1
        }

        test("addKey rechaza un nombre duplicado ignorando acentos") {
            val base =
                Taxonomy
                    .empty(clubId)
                    .addKey("Nivel")
                    .shouldBeRight()
                    .taxonomy
            base.addKey("Nível").shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("nombre", "Nível"))
        }

        test("addValue rechaza un valor duplicado dentro de la misma key") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val tax =
                nivel.taxonomy
                    .addValue(nivel.changed.id, "Alto")
                    .shouldBeRight()
                    .taxonomy
            tax.addValue(nivel.changed.id, "alto").shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("valor", "alto"))
        }

        test("el mismo valor en dos keys distintas se permite (unicidad por key, no por club)") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val terreno = nivel.taxonomy.addKey("terreno").shouldBeRight()
            val afterNivel = terreno.taxonomy.addValue(nivel.changed.id, "alto").shouldBeRight()
            afterNivel.taxonomy.addValue(terreno.changed.id, "alto").shouldBeRight()
        }

        test("renameKey a un nombre de otra key activa devuelve DuplicateLabel") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val terreno = nivel.taxonomy.addKey("terreno").shouldBeRight()
            terreno.taxonomy
                .renameKey(terreno.changed.id, "Nivel")
                .shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("nombre", "Nivel"))
        }

        test("renameKey a su propio nombre con otra capitalización es válido") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val renamed = nivel.taxonomy.renameKey(nivel.changed.id, "NIVEL").shouldBeRight()
            renamed.changed.label.value shouldBe "NIVEL"
        }

        // --- archivado ------------------------------------------------------------------------------------------

        test("archiveKey saca la key de activeKeys pero la conserva en keys") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val tax =
                nivel.taxonomy
                    .archiveKey(nivel.changed.id, at)
                    .shouldBeRight()
                    .taxonomy
            tax.activeKeys().size shouldBe 0
            tax.keys.size shouldBe 1
            tax.findKey(nivel.changed.id)?.archivedAt shouldBe at
        }

        test("un nombre archivado se libera para reutilizarlo en una key activa nueva") {
            val nivel = Taxonomy.empty(clubId).addKey("Nivel").shouldBeRight()
            val archived =
                nivel.taxonomy
                    .archiveKey(nivel.changed.id, at)
                    .shouldBeRight()
                    .taxonomy
            val recreated = archived.addKey("Nivel").shouldBeRight().taxonomy
            recreated.keys.size shouldBe 2
            recreated.activeKeys().size shouldBe 1
        }

        test("assignableValues excluye valores archivados y valores de keys archivadas") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val alto = nivel.taxonomy.addValue(nivel.changed.id, "alto").shouldBeRight()
            val bajo = alto.taxonomy.addValue(nivel.changed.id, "bajo").shouldBeRight()
            val afterValueArchived =
                bajo.taxonomy
                    .archiveValue(bajo.changed.id, at)
                    .shouldBeRight()
                    .taxonomy
            afterValueArchived.assignableValues().map { it.label.value } shouldBe listOf("alto")
            val afterKeyArchived = afterValueArchived.archiveKey(nivel.changed.id, at).shouldBeRight().taxonomy
            afterKeyArchived.assignableValues().size shouldBe 0
        }

        test("reactivateKey devuelve la key a activeKeys") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val archived =
                nivel.taxonomy
                    .archiveKey(nivel.changed.id, at)
                    .shouldBeRight()
                    .taxonomy
            val reactivated = archived.reactivateKey(nivel.changed.id).shouldBeRight().taxonomy
            reactivated.activeKeys().size shouldBe 1
            reactivated.findKey(nivel.changed.id)?.archivedAt shouldBe null
        }

        test("reactivar una key cuyo nombre ya recrearon como activa devuelve DuplicateLabel") {
            val nivel = Taxonomy.empty(clubId).addKey("Nivel").shouldBeRight()
            val archived =
                nivel.taxonomy
                    .archiveKey(nivel.changed.id, at)
                    .shouldBeRight()
                    .taxonomy
            val recreated = archived.addKey("nivel").shouldBeRight().taxonomy
            recreated
                .reactivateKey(nivel.changed.id)
                .shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("nombre", "Nivel"))
        }

        test("reactivar una key no reactiva sus valores archivados (sin cascada)") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val alto = nivel.taxonomy.addValue(nivel.changed.id, "alto").shouldBeRight()
            val valueArchived =
                alto.taxonomy
                    .archiveValue(alto.changed.id, at)
                    .shouldBeRight()
                    .taxonomy
            val keyArchived = valueArchived.archiveKey(nivel.changed.id, at).shouldBeRight().taxonomy
            val keyReactivated = keyArchived.reactivateKey(nivel.changed.id).shouldBeRight().taxonomy
            keyReactivated.findValue(alto.changed.id)?.isActive shouldBe false
        }

        test("archiveKey es idempotente: conserva el archivedAt original") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val later = Instant.parse("2026-08-01T00:00:00Z")
            val first =
                nivel.taxonomy
                    .archiveKey(nivel.changed.id, at)
                    .shouldBeRight()
                    .taxonomy
            val second = first.archiveKey(nivel.changed.id, later).shouldBeRight().taxonomy
            second.findKey(nivel.changed.id)?.archivedAt shouldBe at
        }

        test("addValue sobre una key archivada devuelve Conflict") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val archived =
                nivel.taxonomy
                    .archiveKey(nivel.changed.id, at)
                    .shouldBeRight()
                    .taxonomy
            archived.addValue(nivel.changed.id, "alto").shouldBeLeft(ClubTaxonomiaError.Conflict("tag archivado"))
        }

        test("archiveValue y reactivateValue son simétricos y liberan el nombre dentro de la key") {
            val nivel = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val alto = nivel.taxonomy.addValue(nivel.changed.id, "alto").shouldBeRight()
            val archived =
                alto.taxonomy
                    .archiveValue(alto.changed.id, at)
                    .shouldBeRight()
                    .taxonomy
            val recreated = archived.addValue(nivel.changed.id, "alto").shouldBeRight().taxonomy
            recreated.assignableValues().map { it.label.value } shouldBe listOf("alto")
            recreated
                .reactivateValue(alto.changed.id)
                .shouldBeLeft(ClubTaxonomiaError.DuplicateLabel("valor", "alto"))
        }

        // --- identidad y errores de búsqueda --------------------------------------------------------------------

        test("addKey genera un id nuevo y lo devuelve en changed") {
            val a = Taxonomy.empty(clubId).addKey("nivel").shouldBeRight()
            val b = a.taxonomy.addKey("terreno").shouldBeRight()
            a.changed.id shouldNotBe b.changed.id
        }

        test("renameKey con un id inexistente devuelve TagKeyNotFound") {
            val ghost = TagKeyId.of(UUID.fromString("00000000-0000-0000-0000-0000000000ff"))
            Taxonomy.empty(clubId).renameKey(ghost, "x").shouldBeLeft(ClubTaxonomiaError.TagKeyNotFound)
        }

        test("renameValue con un id inexistente devuelve TagValueNotFound") {
            val ghost = TagValueId.of(UUID.fromString("00000000-0000-0000-0000-0000000000fe"))
            Taxonomy.empty(clubId).renameValue(ghost, "x").shouldBeLeft(ClubTaxonomiaError.TagValueNotFound)
        }

        test("addValue conserva la metadata tipada Race") {
            val objetivo = Taxonomy.empty(clubId).addKey("objetivo").shouldBeRight()
            val race = TagValueMetadata.Race(LocalDate.of(2026, 12, 6), Distance.K42)
            val created =
                objetivo.taxonomy.addValue(objetivo.changed.id, "Maratón de Valencia", race).shouldBeRight()
            created.changed.metadata shouldBe race
        }
    })
