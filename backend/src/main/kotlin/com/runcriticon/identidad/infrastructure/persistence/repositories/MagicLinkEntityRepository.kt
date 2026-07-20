package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.infrastructure.persistence.entities.MagicLinkEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA. No se anota con @Repository (Spring Data lo registra solo); la malla de autorización
 * (@AuthScope/@NoAuthScope) se aplica en el adaptador [MagicLinkRepositoryImpl].
 */
interface MagicLinkEntityRepository : JpaRepository<MagicLinkEntity, UUID> {
    fun findByTokenHash(tokenHash: String): MagicLinkEntity?
}
