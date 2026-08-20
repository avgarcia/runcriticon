package com.runcriticon.auditoria.domain

import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant
import java.util.UUID

/**
 * Asiento de auditoría de autorización (ADR-0009 D15-D17). Dominio puro, append-only: no tiene invariantes de
 * estado, es la constancia de un hecho ya ocurrido en otro módulo.
 *
 * @property actorId quién intentó/ejerció la acción; `null` tras anonimización (derecho al olvido, D17).
 * @property sujetoId tercero sobre el que recaía la operación, cuando lo hay; `null` tras anonimización.
 * @property recurso recurso y acción de la matriz de autorización, ej. `"PLAN:PUBLISH"`, o el dato leído.
 * @property motivo motivo de la denegación (solo [AuditEventType.ACCESO_DENEGADO]); `null` en accesos concedidos.
 */
data class AuditEvent(
    val id: AuditEventId,
    val clubId: ClubId,
    val type: AuditEventType,
    val actorId: UUID?,
    val sujetoId: UUID?,
    val recurso: String,
    val motivo: String?,
    val occurredAt: Instant,
)
