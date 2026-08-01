package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
     * Cuenta los usuarios del club con un rol dado excluyendo un estado. Sostiene la regla que impide dejar el club sin
     * ningún administrador capaz de entrar: se excluye `DESACTIVADO` porque un admin desactivado no puede iniciar
     * sesión, así que no sirve para recuperar el club.
     */
    @Query(
        "select count(u) from UserEntity u " +
            "where u.clubId = :clubId and u.role = :role and u.status <> :excludedStatus",
    )
    fun countByClubIdAndRoleExcludingStatus(
        @Param("clubId") clubId: UUID,
        @Param("role") role: String,
        @Param("excludedStatus") excludedStatus: String,
    ): Long

    /** Borra al usuario al ejercer el derecho de supresión. Sus filas dependientes deben borrarse antes (FK). */
    @Modifying
    @Query("delete from UserEntity u where u.clubId = :clubId and u.id = :id")
    fun deleteByClubIdAndId(
        @Param("clubId") clubId: UUID,
        @Param("id") id: UUID,
    ): Int
}
