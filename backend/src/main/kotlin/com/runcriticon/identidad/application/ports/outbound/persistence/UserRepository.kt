package com.runcriticon.identidad.application.ports.outbound.persistence

import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId

/**
 * Puerto de persistencia del agregado [User]. La malla anti-IDOR exige que cada método público del adaptador declare
 * `@AuthScope` o `@NoAuthScope`.
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
     * Busca un usuario por su id SIN sesión activa: la autorización la aporta el token de invitación, no el principal
     * (que aún no existe). Filtra por club igualmente.
     */
    fun findByIdUnscoped(
        clubId: ClubId,
        userId: UserId,
    ): User?

    /**
     * Lista los usuarios del club con el rol indicado.
     * Filtra por club: nunca devuelve usuarios de otro club.
     */
    fun listByClubAndRole(
        clubId: ClubId,
        role: Role,
    ): List<User>

    /** Persiste un usuario nuevo. */
    fun save(user: User)

    /**
     * Cuenta los usuarios del club con [role] cuyo estado **no** es [excludedStatus]. Sostiene la regla que impide
     * eliminar al último administrador con capacidad de entrar al club.
     */
    fun countByRoleExcludingStatus(
        clubId: ClubId,
        role: Role,
        excludedStatus: UserStatus,
    ): Long

    /**
     * Borra físicamente al usuario. Las filas que lo referencian (invitaciones, magic links, histórico de contraseñas)
     * deben haberse borrado antes: sus claves ajenas no tienen `ON DELETE CASCADE`.
     */
    fun deleteById(
        clubId: ClubId,
        userId: UserId,
    )
}
