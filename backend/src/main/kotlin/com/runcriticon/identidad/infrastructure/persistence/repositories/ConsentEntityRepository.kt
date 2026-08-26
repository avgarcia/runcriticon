package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.infrastructure.persistence.entities.ConsentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Spring Data JPA. No se anota con @Repository (Spring Data lo registra solo); la malla de autorización
 * (@AuthScope/@NoAuthScope) se aplica en el adaptador [ConsentRepositoryImpl].
 */
interface ConsentEntityRepository : JpaRepository<ConsentEntity, UUID> {
    fun findFirstByClubIdAndUserIdOrderByGrantedAtDesc(
        clubId: UUID,
        userId: UUID,
    ): ConsentEntity?

    @Modifying
    @Query("delete from ConsentEntity c where c.clubId = :clubId and c.userId = :userId")
    fun deleteByClubIdAndUserId(
        @Param("clubId") clubId: UUID,
        @Param("userId") userId: UUID,
    ): Int
}
