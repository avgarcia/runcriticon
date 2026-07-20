package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.infrastructure.persistence.mappers.AuditEventMapper
import com.runcriticon.identidad.infrastructure.persistence.mappers.AuditEventMapperImpl
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.stereotype.Repository

/**
 * Adaptador del puerto [AuditTrail] sobre Spring Data. Persiste el asiento en `identidad.evento_auditoria` dentro de la
 * transacción del caso de uso que lo invoca.
 */
@Repository
class AuditTrailImpl(
    private val jpa: AuditEventEntityRepository,
) : AuditTrail {
    private val mapper: AuditEventMapper = AuditEventMapperImpl

    @NoAuthScope("escritura de auditoría del sistema; no devuelve datos de cliente")
    override fun record(entry: AuditEntry) {
        jpa.save(mapper.toEntity(entry))
    }
}
