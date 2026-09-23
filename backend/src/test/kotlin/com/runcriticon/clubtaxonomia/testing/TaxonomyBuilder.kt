package com.runcriticon.clubtaxonomia.testing

import com.runcriticon.clubtaxonomia.domain.tag.TagKeyType
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.TestClubs
import io.kotest.assertions.arrow.core.shouldBeRight

/**
 * Builder de [Taxonomy] (`testing-de-modulos.md` §8), sobre las factorías reales del agregado
 * (`Taxonomy.empty` + `addKey`/`addValue`). Cada [withKey] deja la key recién creada como "actual" para que
 * [withValue] la use por defecto.
 */
class TaxonomyBuilder {
    private var clubId: ClubId = TestClubs.newClub()
    private var taxonomy: Taxonomy = Taxonomy.empty(clubId)
    private var lastKeyLabel: String? = null

    fun inClub(clubId: ClubId) =
        apply {
            this.clubId = clubId
            taxonomy = Taxonomy.empty(clubId)
        }

    fun withKey(
        label: String,
        type: TagKeyType = TagKeyType.SIMPLE,
    ) = apply {
        taxonomy = taxonomy.addKey(label, type).shouldBeRight().taxonomy
        lastKeyLabel = label
    }

    /** Añade [label] a la key creada por la última llamada a [withKey]. */
    fun withValue(
        label: String,
        metadata: TagValueMetadata = TagValueMetadata.Empty,
    ) = apply {
        val keyLabel =
            checkNotNull(lastKeyLabel) { "TaxonomyBuilder.withValue: llama a withKey(...) antes de withValue(...)" }
        val key = taxonomy.activeKeys().first { it.label.value == keyLabel }
        taxonomy = taxonomy.addValue(key.id, label, metadata).shouldBeRight().taxonomy
    }

    fun build(): Taxonomy = taxonomy
}
