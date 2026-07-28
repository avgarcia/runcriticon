package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import com.runcriticon.clubtaxonomia.infrastructure.persistence.entities.TagValueEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA. No se anota con @Repository (Spring Data lo registra solo); la malla de autorización
 * (@AuthScope/@NoAuthScope) se aplica en el adaptador [TaxonomyRepositoryImpl].
 */
interface TagValueEntityRepository : JpaRepository<TagValueEntity, UUID> {
    fun findAllByClubId(clubId: UUID): List<TagValueEntity>
}
