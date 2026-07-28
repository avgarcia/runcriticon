package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.tenancy.ClubId

/**
 * Puerto de persistencia del agregado [Taxonomy] de un club. La taxonomía se carga y se guarda entera (es pequeña:
 * decenas de ejes, cientos de valores). La malla anti-IDOR exige que cada método público del adaptador declare
 * `@AuthScope` o `@NoAuthScope`.
 */
interface TaxonomyRepository {
    /** Taxonomía del club. Nunca es null: si el club no tiene ejes todavía devuelve [Taxonomy.empty]. */
    fun findByClub(clubId: ClubId): Taxonomy

    /**
     * Persiste el estado completo del agregado. El [clubId] va explícito (no se infiere de la taxonomía) para que el
     * aspecto de `@AuthScope` verifique que coincide con el del principal antes de escribir.
     */
    fun save(
        clubId: ClubId,
        taxonomy: Taxonomy,
    )
}
