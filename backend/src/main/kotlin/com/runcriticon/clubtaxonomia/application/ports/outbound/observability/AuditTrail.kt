package com.runcriticon.clubtaxonomia.application.ports.outbound.observability

import com.runcriticon.clubtaxonomia.domain.audit.AuditEntry
import com.runcriticon.shared.tenancy.ClubId

/**
 * Puerto de escritura del registro de auditoría local de `club_taxonomia`. La implementación persiste el asiento en
 * `club_taxonomia.evento_auditoria`, participando de la transacción del caso de uso que lo invoca: una acción que
 * falla no deja rastro de éxito.
 */
interface AuditTrail {
    fun record(
        clubId: ClubId,
        entry: AuditEntry,
    )
}
