package com.runcriticon.auditoria.infrastructure.rest

import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventFilter
import com.runcriticon.auditoria.application.usecases.events.ListAuditEventsQuery
import com.runcriticon.auditoria.domain.AuditEventType
import com.runcriticon.auditoria.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.auditoria.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.api.rest.ErrorResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

/** Consulta forense del log de auditoría de autorización (ADR-0009 D17). Solo ADMIN. */
@RestController
@RequestMapping("/api/auditoria/eventos")
class AuditEventController(
    private val listAuditEvents: ListAuditEventsQuery,
    private val principalProvider: PrincipalProvider,
) {
    /**
     * GET /api/auditoria/eventos?actorId=&sujetoId=&tipo=&desde=&hasta= — `tipo` inválido se rechaza aquí, no en
     * el caso de uso: es un error de forma del query param, no una regla de negocio de `AuditoriaError`.
     */
    @GetMapping
    @Authorize("AUDIT_EVENT:LIST")
    fun list(
        @RequestParam(required = false) actorId: UUID?,
        @RequestParam(required = false) sujetoId: UUID?,
        @RequestParam(required = false) tipo: String?,
        @RequestParam(required = false) desde: OffsetDateTime?,
        @RequestParam(required = false) hasta: OffsetDateTime?,
    ): ResponseEntity<*> {
        val type = tipo?.let(::parseType)
        if (tipo != null && type == null) return badTipo(tipo)

        val filter =
            AuditEventFilter(
                actorId = actorId,
                sujetoId = sujetoId,
                type = type,
                desde = desde?.toInstant(),
                hasta = hasta?.toInstant(),
            )

        return listAuditEvents.execute(principalProvider.current(), filter).fold(
            { error -> error.toErrorResponse() },
            { events -> ResponseEntity.ok(events.toResponse()) },
        )
    }

    private fun parseType(raw: String): AuditEventType? = runCatching { AuditEventType.valueOf(raw) }.getOrNull()

    private fun badTipo(raw: String): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "INVALID_INPUT", field = "tipo", message = "Tipo de evento desconocido: $raw"),
        )
}
