package com.runcriticon.auditoria.infrastructure.persistence.mappers

import com.runcriticon.auditoria.domain.AuditEvent
import com.runcriticon.auditoria.infrastructure.persistence.entities.AuditoriaEventEntity
import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping

/** Mapeo del asiento de dominio a entity para la escritura (`AuditEventRepositoryImpl.save`). */
@Konverter
internal interface AuditEventMapper {
    @Konvert(
        mappings = [
            Mapping(target = "id", expression = "it.id.value"),
            Mapping(target = "clubId", expression = "it.clubId.value"),
            Mapping(target = "type", expression = "it.type.name"),
        ],
    )
    fun toEntity(event: AuditEvent): AuditoriaEventEntity
}
