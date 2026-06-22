package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.stereotype.Repository

/**
 * Adaptador del puerto [AuditTrail] sobre Spring Data. Persiste el asiento en
 * `identidad.evento_auditoria` dentro de la transacción del caso de uso que lo invoca.
 */
@Repository
class AuditTrailImpl(
    private val jpa: AuditEventEntityRepository,
) : AuditTrail {
    @NoAuthScope("escritura de auditoría del sistema; no devuelve datos de cliente (ADR-0003 D15)")
    override fun record(entry: AuditEntry) {
        jpa.save(entry.toEntity())
    }
}
