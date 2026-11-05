package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Spring Data JPA. No se anota con @Repository (Spring Data lo registra solo); la malla de autorización
 * (@AuthScope/@NoAuthScope) se aplica en el adaptador [UserRepositoryImpl].
 */
interface UserEntityRepository : JpaRepository<UserEntity, UUID> {
    fun findByClubIdAndNormalizedEmail(
        clubId: UUID,
        normalizedEmail: String,
    ): UserEntity?

    fun findByClubIdAndId(
        clubId: UUID,
        id: UUID,
    ): UserEntity?

    fun findByClubIdAndRoleOrderByNameAsc(
        clubId: UUID,
        role: String,
    ): List<UserEntity>

    /**
     * Devuelve solo el estado de la cuenta por id (proyección ligera para el gate-check de estado). Sin `club_id`: es
     * un control de seguridad transversal, no una consulta de datos de cliente; el `id` (UUID) es no adivinable y no
     * expone PII.
     */
    @Query("select u.status from UserEntity u where u.id = :id")
    fun findStatusById(id: UUID): String?

    /**
     * Cuenta los usuarios del club con un rol dado en alguno de los estados indicados. Sostiene la regla que impide
     * dejar el club sin ningún administrador capaz de entrar.
     */
    fun countByClubIdAndRoleAndStatusIn(
        clubId: UUID,
        role: String,
        statuses: Collection<String>,
    ): Long

    /**
     * Lee al usuario bloqueando su fila hasta el fin de la transacción. Serializa los cambios de estado de la baja con
     * la supresión del job, que bloquea la misma fila ([lockExpiredDeletion]).
     */
    @Query(
        value = "SELECT * FROM identidad.usuario WHERE club_id = :clubId AND id = :id FOR UPDATE",
        nativeQuery = true,
    )
    fun findForUpdate(
        @Param("clubId") clubId: UUID,
        @Param("id") id: UUID,
    ): UserEntity?

    /**
     * Bloquea la fila del usuario solo si sigue en `BAJA` con la gracia vencida, y salta la fila si otra transacción
     * ya la tiene bloqueada (ADR-0017 D9): una segunda ejecución del job no la encuentra, en vez de esperar a suprimir
     * dos veces la misma cuenta.
     */
    @Query(
        value =
            "SELECT * FROM identidad.usuario WHERE club_id = :clubId AND id = :id " +
                "AND estado = 'BAJA' AND baja_en < :cutoff FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun lockExpiredDeletion(
        @Param("clubId") clubId: UUID,
        @Param("id") id: UUID,
        @Param("cutoff") cutoff: Instant,
    ): UserEntity?

    /** Las cuentas de todos los clubes en un estado dado marcadas para baja antes de [cutoff]. */
    fun findByStatusAndMarkedForDeletionAtBefore(
        status: String,
        cutoff: Instant,
    ): List<UserEntity>

    /** Borra al usuario al ejercer el derecho de supresión. Sus filas dependientes deben borrarse antes (FK). */
    @Modifying
    @Query("delete from UserEntity u where u.clubId = :clubId and u.id = :id")
    fun deleteByClubIdAndId(
        @Param("clubId") clubId: UUID,
        @Param("id") id: UUID,
    ): Int
}
