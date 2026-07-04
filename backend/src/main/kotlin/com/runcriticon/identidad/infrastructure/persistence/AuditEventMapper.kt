package com.runcriticon.identidad.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.domain.audit.AuditEntry

/**
 * Mapeo del asiento de auditoría de dominio a entity. El `id` (UUID v7) lo genera el adaptador:
 * el asiento es append-only y no necesita identidad en el dominio.
 */
internal fun AuditEntry.toEntity(): AuditEventEntity =
    AuditEventEntity(
        id = UuidCreator.getTimeOrderedEpoch(),
        type = type.name,
        actorId = actorId,
        subjectId = subjectId,
        occurredAt = occurredAt,
        metadata = metadata,
    )
