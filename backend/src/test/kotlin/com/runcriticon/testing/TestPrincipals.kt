package com.runcriticon.testing

import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role

/**
 * Pares de [Principal] para los tests de acceso cruzado que exige ADR-0009 D14: "dos principales del mismo
 * rol que no deberían cruzarse".
 */
object TestPrincipals {
    fun twoCoachesSameClub(): Pair<Principal, Principal> {
        val club = TestClubs.newClub()
        return PrincipalBuilder().coach().inClub(club).build() to
            PrincipalBuilder().coach().inClub(club).build()
    }

    fun twoStudentsSameClub(): Pair<Principal, Principal> {
        val club = TestClubs.newClub()
        return PrincipalBuilder().student().inClub(club).build() to
            PrincipalBuilder().student().inClub(club).build()
    }

    fun adminAndCoach(): Pair<Principal, Principal> {
        val club = TestClubs.newClub()
        return PrincipalBuilder().admin().inClub(club).build() to
            PrincipalBuilder().coach().inClub(club).build()
    }

    /** El mismo rol, cada uno en su propio club — para el caso entre clubes (hoy no alcanzable en el MVP mono-club). */
    fun sameRoleInTwoClubs(role: Role): Pair<Principal, Principal> {
        val (clubA, clubB) = TestClubs.twoClubs()
        return PrincipalBuilder().role(role).inClub(clubA).build() to
            PrincipalBuilder().role(role).inClub(clubB).build()
    }
}
