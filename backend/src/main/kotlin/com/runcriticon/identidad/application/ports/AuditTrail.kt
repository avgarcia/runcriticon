package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.audit.AuditEntry

/**
 * Puerto de escritura del registro de auditoría de identidad (ADR-0003 D15). La implementación
 * persiste el asiento en `identidad.evento_auditoria`, participando de la transacción del caso de
 * uso: una acción que falla no deja rastro de éxito.
 */
interface AuditTrail {
    fun record(entry: AuditEntry)
}
