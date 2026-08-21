package com.runcriticon.identidad.application.ports.outbound.observability

import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.user.Email
import java.util.UUID

/**
 * Puerto de escritura del registro de auditoría de identidad. La implementación persiste el asiento en
 * `identidad.evento_auditoria`, participando de la transacción del caso de uso: una acción que falla no deja rastro de
 * éxito.
 */
interface AuditTrail {
    fun record(entry: AuditEntry)

    /**
     * Anonimiza los asientos que mencionan a `personId` (como actor o como sujeto) y los que quedaron ligados a su
     * email por `email_hash` en un evento de rate-limiting sin actor ni sujeto (flujo anónimo). No borra filas: solo
     * despoja los identificadores pseudónimos. Devuelve cuántos asientos se tocaron; llamarlo dos veces con la misma
     * persona es un no-op la segunda vez.
     */
    fun anonymize(
        personId: UUID,
        email: Email,
    ): Int
}
