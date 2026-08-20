package com.runcriticon.auditoria.infrastructure.rest.mappers

import com.runcriticon.auditoria.domain.AuditEvent
import com.runcriticon.auditoria.domain.AuditEventType
import com.runcriticon.auditoria.domain.AuditoriaError
import com.runcriticon.shared.api.rest.AuditEventResponse
import com.runcriticon.shared.api.rest.AuditEventsResponse
import com.runcriticon.shared.api.rest.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.ZoneOffset

fun List<AuditEvent>.toResponse(): AuditEventsResponse = AuditEventsResponse(eventos = map { it.toResponse() })

private fun AuditEvent.toResponse(): AuditEventResponse =
    AuditEventResponse(
        id = id.value,
        tipo = type.toResponse(),
        actorId = actorId,
        sujetoId = sujetoId,
        recurso = recurso,
        motivo = motivo,
        ocurridoEn = occurredAt.atOffset(ZoneOffset.UTC),
    )

private fun AuditEventType.toResponse(): AuditEventResponse.Tipo =
    when (this) {
        AuditEventType.ACCESO_DENEGADO -> AuditEventResponse.Tipo.ACCESO_DENEGADO
        AuditEventType.ACCESO_DATOS_SENSIBLES -> AuditEventResponse.Tipo.ACCESO_DATOS_SENSIBLES
    }

/** Mapea [AuditoriaError] a respuesta HTTP estructurada. Mismo criterio que `PlanErrorMapper`. */
fun AuditoriaError.toErrorResponse(): ResponseEntity<ErrorResponse> =
    when (this) {
        AuditoriaError.Forbidden ->
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse(code = "FORBIDDEN", field = null, message = "Acceso denegado"),
            )

        is AuditoriaError.InvalidInput ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "INVALID_INPUT", field = field, message = "Revisa los datos introducidos"),
            )
    }
