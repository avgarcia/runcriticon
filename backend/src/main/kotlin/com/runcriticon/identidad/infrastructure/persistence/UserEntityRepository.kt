package com.runcriticon.identidad.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/**
 * Spring Data JPA. No se anota con @Repository (Spring Data lo registra solo); la malla de
 * autorización (@AuthScope/@NoAuthScope) se aplica en el adaptador [UserRepositoryImpl].
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
     * Devuelve solo el estado de la cuenta por id (proyección ligera para el gate-check de estado,
     * LAL-13). Sin `club_id`: es un control de seguridad transversal, no una consulta de datos de
     * cliente; el `id` (UUID) es no adivinable y no expone PII.
     */
    @Query("select u.status from UserEntity u where u.id = :id")
    fun findStatusById(id: UUID): String?
}
