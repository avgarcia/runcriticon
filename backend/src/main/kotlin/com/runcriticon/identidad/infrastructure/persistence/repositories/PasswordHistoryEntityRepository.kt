package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.infrastructure.persistence.entities.PasswordHistoryEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
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
}
