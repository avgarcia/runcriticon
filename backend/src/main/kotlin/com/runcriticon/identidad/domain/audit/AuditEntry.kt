package com.runcriticon.identidad.domain.audit

import com.runcriticon.identidad.domain.user.UserId
import java.time.Instant

/**
 * Asiento de auditoría de identidad. Dominio puro: el caso de uso construye el asiento y lo entrega al puerto de
 * auditoría, que lo persiste en la misma transacción.
 *
 * @property type tipo del evento auditado.
 * @property actorId quién ejecuta la acción (admin que invita…); `null` para acciones del sistema.
 * @property subjectId sobre quién recae la acción (usuario invitado…); `null` si no aplica.
 * @property occurredAt instante del hecho de negocio.
 * @property metadata pares clave-valor opcionales (p. ej. `email_hash`, `ip` en eventos de rate-limiting); se persisten
 * como JSONB. `null` cuando no hay contexto extra.
 */
data class AuditEntry(
    val type: AuditEventType,
    val actorId: UserId?,
    val subjectId: UserId?,
    val occurredAt: Instant,
    val metadata: Map<String, String>? = null,
)
