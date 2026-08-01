package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.infrastructure.persistence.entities.PasswordHistoryEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Spring Data JPA del histórico de contraseñas. La malla de autorización (@NoAuthScope) se aplica en el adaptador
 * [PasswordHistoryRepositoryImpl].
 */
interface PasswordHistoryEntityRepository : JpaRepository<PasswordHistoryEntity, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(
        userId: UUID,
        pageable: Pageable,
    ): List<PasswordHistoryEntity>

    /** Borra el histórico de contraseñas del usuario al ejercer el derecho de supresión, en un solo `DELETE`. */
    @Modifying
    @Query("delete from PasswordHistoryEntity p where p.clubId = :clubId and p.userId = :userId")
    fun deleteByClubIdAndUserId(
        @Param("clubId") clubId: UUID,
        @Param("userId") userId: UUID,
    ): Int
}
