package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import com.runcriticon.clubtaxonomia.infrastructure.persistence.entities.ClubTaxonomiaAuditEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA del asiento de auditoría local. Sin `@Repository` explícito (Spring Data lo registra solo); la
 * malla de autorización se aplica en el adaptador [ClubTaxonomiaAuditTrailImpl].
 *
 * **No se llama `AuditEventEntityRepository`** pese a ser el nombre natural: `identidad` ya tiene una interfaz con ese
 * simple name para su propia `evento_auditoria`, y Spring registra los repositorios de datos por simple class name sin
 * distinguir paquete — mismo motivo documentado en `auditoria.AuditoriaEventEntityRepository`.
 */
interface ClubTaxonomiaAuditEventEntityRepository : JpaRepository<ClubTaxonomiaAuditEventEntity, UUID>
