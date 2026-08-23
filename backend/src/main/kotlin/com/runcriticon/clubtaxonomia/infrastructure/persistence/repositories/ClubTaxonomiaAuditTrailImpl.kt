package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.AuditTrail
import com.runcriticon.clubtaxonomia.domain.audit.AuditEntry
import com.runcriticon.clubtaxonomia.infrastructure.persistence.entities.ClubTaxonomiaAuditEventEntity
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.stereotype.Repository

/**
 * Adaptador del puerto [AuditTrail] sobre Spring Data. Persiste el asiento en `club_taxonomia.evento_auditoria` dentro
 * de la transacción del caso de uso que lo invoca.
 *
 * Sin mapper Konvert: a diferencia del de `identidad`, este asiento tiene dos fuentes (`clubId` explícito + [AuditEntry]
 * del dominio, que no lleva `clubId` porque no todo módulo con auditoría local es club-scoped), y el mapeo 1:1 restante
 * es trivial — no compensa forzarlo a un mapper generado.
 *
 * **Nombre con prefijo `ClubTaxonomia`**: `identidad` ya tiene un `AuditTrailImpl` (`@Repository`, mismo simple name
 * chocaría al registrar el bean); mismo motivo que el resto de clases de este paquete.
 */
@Repository
class ClubTaxonomiaAuditTrailImpl(
    private val jpa: ClubTaxonomiaAuditEventEntityRepository,
) : AuditTrail {
    @AuthScope(Scope.CLUB)
    override fun record(
        clubId: ClubId,
        entry: AuditEntry,
    ) {
        jpa.save(
            ClubTaxonomiaAuditEventEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                clubId = clubId.value,
                type = entry.type.name,
                actorId = entry.actorId,
                subjectId = entry.subjectId,
                occurredAt = entry.occurredAt,
                metadata = entry.metadata,
            ),
        )
    }
}
