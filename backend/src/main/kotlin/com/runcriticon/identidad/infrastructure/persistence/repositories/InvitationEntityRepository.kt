package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.infrastructure.persistence.entities.InvitationEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Spring Data JPA. No se anota con @Repository (Spring Data lo registra solo); la malla de autorización
 * (@AuthScope/@NoAuthScope) se aplica en el adaptador [InvitationRepositoryImpl].
 */
interface InvitationEntityRepository : JpaRepository<InvitationEntity, UUID> {
    fun findByTokenHash(tokenHash: String): InvitationEntity?

    fun findTopByUserIdOrderByIssuedAtDesc(userId: UUID): InvitationEntity?

    /**
     * Borra las invitaciones del usuario al ejercer el derecho de supresión. `@Modifying` con JPQL en vez de derived
     * query (`deleteByClubIdAndUserId`): esta emite un solo `DELETE`, mientras que la derivada carga las entidades y
     * las borra una a una.
     */
    @Modifying
    @Query("delete from InvitationEntity i where i.clubId = :clubId and i.userId = :userId")
    fun deleteByClubIdAndUserId(
        @Param("clubId") clubId: UUID,
        @Param("userId") userId: UUID,
    ): Int

    /**
     * Anula `invitado_por` en las invitaciones que emitió el usuario suprimido. La columna es nula para las emitidas
     * antes de que se registrara el invitador, así que `NULL` ya es un valor que el resto del código sabe leer.
     */
    @Modifying
    @Query(
        "update InvitationEntity i set i.invitedBy = null " +
            "where i.clubId = :clubId and i.invitedBy = :inviterId",
    )
    fun clearInviterByClubIdAndInviter(
        @Param("clubId") clubId: UUID,
        @Param("inviterId") inviterId: UUID,
    ): Int
}
