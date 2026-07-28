package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import com.runcriticon.clubtaxonomia.infrastructure.persistence.entities.TagKeyEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA. No se anota con @Repository (Spring Data lo registra solo); la malla de autorización
 * (@AuthScope/@NoAuthScope) se aplica en el adaptador [TaxonomyRepositoryImpl].
 */
interface TagKeyEntityRepository : JpaRepository<TagKeyEntity, UUID> {
    fun findAllByClubId(clubId: UUID): List<TagKeyEntity>
}
