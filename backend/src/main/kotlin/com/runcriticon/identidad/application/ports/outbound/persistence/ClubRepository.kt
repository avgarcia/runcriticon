package com.runcriticon.identidad.application.ports.outbound.persistence

import com.runcriticon.identidad.domain.club.Club
import com.runcriticon.shared.tenancy.ClubId

/**
 * Puerto de persistencia del agregado [Club]. La malla anti-IDOR exige que cada método público del adaptador declare
 * `@AuthScope` o `@NoAuthScope`.
 */
interface ClubRepository {
    /** Busca el club por su id, que es el mismo [ClubId] del principal; devuelve null si no existe fila. */
    fun findById(clubId: ClubId): Club?

    /** Persiste los cambios del club (alta o actualización). */
    fun save(club: Club)
}
