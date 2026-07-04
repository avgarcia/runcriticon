package com.runcriticon.identidad.domain.audit

import java.time.Instant
import java.util.UUID

/**
 * Asiento de auditoría de identidad (ADR-0003 D15). Dominio puro: el caso de uso construye el
 * asiento y lo entrega al puerto de auditoría, que lo persiste en la misma transacción.
 *
 * @property type tipo del evento auditado.
 * @property actorId quién ejecuta la acción (admin que invita…); `null` para acciones del sistema.
 * @property subjectId sobre quién recae la acción (usuario invitado…); `null` si no aplica.
 * @property occurredAt instante del hecho de negocio.
 * @property metadata pares clave-valor opcionales (p. ej. `email_hash`, `ip` en eventos de
 *   rate-limiting, ADR-0003 D12); se persisten como JSONB. `null` cuando no hay contexto extra.
 */
data class AuditEntry(
    val type: AuditEventType,
    val actorId: UUID?,
    val subjectId: UUID?,
    val occurredAt: Instant,
    val metadata: Map<String, String>? = null,
)
