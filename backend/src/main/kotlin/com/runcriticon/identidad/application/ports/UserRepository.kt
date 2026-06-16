package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.usuario.Email
import com.runcriticon.identidad.domain.usuario.User
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
}
