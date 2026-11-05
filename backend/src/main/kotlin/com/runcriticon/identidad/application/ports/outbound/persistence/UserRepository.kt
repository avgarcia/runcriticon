package com.runcriticon.identidad.application.ports.outbound.persistence

import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant

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
     * Lee solo el estado de la cuenta del usuario dentro del club, sin cargar el agregado: es la consulta del
     * gate-check que corre en cada petición autenticada. Devuelve null si no existe o pertenece a otro club.
     */
    fun findStatus(
        clubId: ClubId,
        userId: UserId,
    ): UserStatus?

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
     * Cuenta los usuarios del club con [role] en alguno de los [statuses]. Sostiene la regla que impide suprimir al
     * último administrador con capacidad de entrar al club.
     */
    fun countByRoleAndStatuses(
        clubId: ClubId,
        role: Role,
        statuses: Set<UserStatus>,
    ): Long

    /**
     * Como [findById], pero bloquea la fila hasta el fin de la transacción. Lo usan los casos de uso que cambian el
     * estado de la baja o suprimen la cuenta, para no pisarse con el job que suprime las bajas vencidas.
     */
    fun findByIdForUpdate(
        clubId: ClubId,
        userId: UserId,
    ): User?

    /**
     * Bloquea la cuenta solo si sigue en [UserStatus.BAJA] y se marcó antes de [cutoff]; si otra transacción ya la
     * tiene bloqueada, la salta y devuelve null en vez de esperar (ADR-0017 D9).
     */
    fun lockExpiredDeletion(
        clubId: ClubId,
        userId: UserId,
        cutoff: Instant,
    ): User?

    /**
     * Las cuentas de **todos los clubes** en [UserStatus.BAJA] marcadas antes de [cutoff]. Solo la usa el job que
     * suprime las bajas vencidas: es un proceso del sistema, sin principal ni club en curso.
     */
    fun findExpiredDeletions(cutoff: Instant): List<User>

    /**
     * Borra físicamente al usuario. Las filas que lo referencian (invitaciones, magic links, histórico de contraseñas)
     * deben haberse borrado antes: sus claves ajenas no tienen `ON DELETE CASCADE`.
     */
    fun deleteById(
        clubId: ClubId,
        userId: UserId,
    )
}
