package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.infrastructure.persistence.entities.MagicLinkEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Spring Data JPA. No se anota con @Repository (Spring Data lo registra solo); la malla de autorización
 * (@AuthScope/@NoAuthScope) se aplica en el adaptador [MagicLinkRepositoryImpl].
 */
interface MagicLinkEntityRepository : JpaRepository<MagicLinkEntity, UUID> {
    fun findByTokenHash(tokenHash: String): MagicLinkEntity?

    /** Borra los magic links del usuario al ejercer el derecho de supresión, en un solo `DELETE`. */
    @Modifying
    @Query("delete from MagicLinkEntity m where m.clubId = :clubId and m.userId = :userId")
    fun deleteByClubIdAndUserId(
        @Param("clubId") clubId: UUID,
        @Param("userId") userId: UUID,
    ): Int
}
