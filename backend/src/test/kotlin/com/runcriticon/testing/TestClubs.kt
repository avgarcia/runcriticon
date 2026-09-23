package com.runcriticon.testing

import com.runcriticon.shared.tenancy.ClubId

/**
 * Clubes de prueba. Cada llamada es un club nuevo: aislar el test propio del bootstrap y de otros tests en
 * paralelo es la regla de la guía (`testing-de-modulos.md` §4) — nunca reutilizar el club fijo de bootstrap.
 */
object TestClubs {
    fun newClub(): ClubId = ClubId.new()

    /** Dos clubes distintos, para los tests que verifican aislamiento entre clubes (ADR-0009 D14). */
    fun twoClubs(): Pair<ClubId, ClubId> = newClub() to newClub()
}
