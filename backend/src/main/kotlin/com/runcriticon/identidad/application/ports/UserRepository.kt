package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.model.ClubId
import com.runcriticon.shared.autorizacion.model.Role

/**
 * Puerto de persistencia del agregado [User] (ADR-0008 D11). La malla anti-IDOR exige que
 * cada método público del adaptador declare `@AuthScope` o `@NoAuthScope`.
 */
interface UserRepository {
    fun findByEmail(
        clubId: ClubId,
        email: Email,
    ): User?

    /** Busca un usuario por su id dentro del club; devuelve null si no existe o pertenece a otro club. */
    fun findById(
        clubId: ClubId,
        userId: UserId,
    ): User?

    /**
     * Busca un usuario por su id SIN sesión activa (activación anónima, LAL-9): la autorización la
     * aporta el token de invitación, no el principal (que aún no existe). Filtra por club igualmente.
     */
    fun findByIdUnscoped(
        clubId: ClubId,
        userId: UserId,
    ): User?

    /**
     * Lista los usuarios del club con el rol indicado (listado admin de entrenadores, LAL-13).
     * Filtra por club: nunca devuelve usuarios de otro club.
     */
    fun listByClubAndRole(
        clubId: ClubId,
        role: Role,
    ): List<User>

    /** Persiste un usuario nuevo (alta por invitación, ADR-0003 D3). */
    fun save(user: User)
}
