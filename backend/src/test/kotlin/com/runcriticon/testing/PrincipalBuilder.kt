package com.runcriticon.testing

import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import java.util.UUID

/**
 * Builder fluent de [Principal] (`testing-de-modulos.md` §8). Sustituye al `fun principal(role)` que cada
 * `*AuthorizationTest` repetía a mano con el mismo club fijo `…0001` — este builder usa un club nuevo por
 * defecto para no acoplar los tests entre sí.
 */
class PrincipalBuilder {
    private var role: Role = Role.ALUMNO
    private var clubId: ClubId = TestClubs.newClub()
    private var userId: UUID = UUID.randomUUID()

    fun role(role: Role) = apply { this.role = role }

    fun admin() = role(Role.ADMIN)

    fun coach() = role(Role.ENTRENADOR)

    fun student() = role(Role.ALUMNO)

    fun inClub(clubId: ClubId) = apply { this.clubId = clubId }

    fun withUserId(userId: UUID) = apply { this.userId = userId }

    fun build(): Principal = Principal(userId = userId, clubId = clubId.value, role = role)
}
