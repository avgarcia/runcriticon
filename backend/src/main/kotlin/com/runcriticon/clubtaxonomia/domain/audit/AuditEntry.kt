package com.runcriticon.clubtaxonomia.domain.audit

import java.time.Instant
import java.util.UUID

/**
 * Asiento de auditoría local de `club_taxonomia`. Dominio puro: el caso de uso lo construye y lo entrega al puerto de
 * auditoría, que lo persiste en la misma transacción.
 *
 * @property type tipo del evento auditado.
 * @property actorId quién ejecuta la acción (admin o entrenador que clasifica).
 * @property subjectId sobre quién recae la acción (el alumno clasificado).
 * @property occurredAt instante del hecho de negocio.
 * @property metadata contexto opcional; en [AuditEventType.TAGS_ALUMNO_ACTUALIZADOS] lleva `antes`/`despues` con los
 * ids de los valores de tag como texto.
 */
data class AuditEntry(
    val type: AuditEventType,
    val actorId: UUID?,
    val subjectId: UUID?,
    val occurredAt: Instant,
    val metadata: Map<String, List<String>>? = null,
)
