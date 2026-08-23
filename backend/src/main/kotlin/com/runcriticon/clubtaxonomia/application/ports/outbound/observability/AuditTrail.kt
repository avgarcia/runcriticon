package com.runcriticon.clubtaxonomia.application.ports.outbound.observability

import com.runcriticon.clubtaxonomia.domain.audit.AuditEntry
import com.runcriticon.shared.tenancy.ClubId
import java.util.UUID

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

    /**
     * Anonimiza los asientos que mencionan a `personId` (como `actorId` o como `subjectId`): los pone a `NULL` sin
     * borrar la fila (ADR-0014 D6, categoría 2). Sin `clubId` en la firma: lo invoca el listener de bajas, que no
     * tiene principal ni un `clubId` de confianza a mano — el filtro es por persona. Devuelve cuántos asientos se
     * tocaron; llamarlo dos veces con la misma persona es un no-op la segunda vez.
     */
    fun anonymize(personId: UUID): Int
}
