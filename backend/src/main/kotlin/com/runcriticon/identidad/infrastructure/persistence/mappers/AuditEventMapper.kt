package com.runcriticon.identidad.infrastructure.persistence.mappers

import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.infrastructure.persistence.entities.AuditEventEntity
import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping

/**
 * Mapeo del asiento de auditoría de dominio a entity. El `id` (UUID v7) lo genera el adaptador: el asiento es
 * append-only y no necesita identidad en el dominio.
 */
@Konverter
internal interface AuditEventMapper {
    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch()"),
            Mapping(target = "type", expression = "it.type.name"),
        ],
    )
    fun toEntity(entry: AuditEntry): AuditEventEntity
}
