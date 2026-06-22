package com.runcriticon.identidad.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA del asiento de auditoría. No se anota con @Repository (Spring Data lo registra
 * solo); la malla de autorización se aplica en el adaptador [AuditTrailImpl].
 */
interface AuditEventEntityRepository : JpaRepository<AuditEventEntity, UUID>
