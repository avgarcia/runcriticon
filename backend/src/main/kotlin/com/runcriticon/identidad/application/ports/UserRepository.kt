package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import java.util.UUID

/**
 * Puerto de persistencia del agregado [User] (ADR-0008 D11). La malla anti-IDOR exige que
 * cada método público del adaptador declare `@AuthScope` o `@NoAuthScope`.
 */
interface UserRepository {
    fun findByEmail(
        clubId: UUID,
        email: Email,
    ): User?

    /** Busca un usuario por su id dentro del club; devuelve null si no existe o pertenece a otro club. */
    fun findById(
        clubId: UUID,
        userId: UserId,
    ): User?

    /** Persiste un usuario nuevo (alta por invitación, ADR-0003 D3). */
    fun save(user: User)
}
