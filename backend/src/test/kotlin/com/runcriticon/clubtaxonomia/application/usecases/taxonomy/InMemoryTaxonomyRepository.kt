package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.tenancy.ClubId

/**
 * Doble en memoria del puerto: los casos de uso encadenan `findByClub` → mutación → `save`, así que un mock sin estado
 * no permitiría comprobar el resultado de dos operaciones seguidas (crear y luego renombrar lo creado).
 */
class InMemoryTaxonomyRepository(
    private var taxonomy: Taxonomy,
) : TaxonomyRepository {
    /** Cuántas veces se ha escrito: un rechazo o un error de dominio no debe dejar rastro. */
    var saveCount: Int = 0
        private set

    override fun findByClub(clubId: ClubId): Taxonomy = taxonomy

    override fun save(
        clubId: ClubId,
        taxonomy: Taxonomy,
    ) {
        this.taxonomy = taxonomy
        saveCount++
    }
}
