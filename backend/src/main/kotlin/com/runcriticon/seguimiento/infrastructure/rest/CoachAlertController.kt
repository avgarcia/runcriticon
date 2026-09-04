package com.runcriticon.seguimiento.infrastructure.rest

import com.runcriticon.seguimiento.application.usecases.alerts.ListCoachAlertsQuery
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Panel de alertas del entrenador (LAL-116, M17). Primer controlador de `seguimiento` que sirve a un
 * ENTRENADOR, no al alumno — a diferencia de `MyPlanController`/`MyMarksController` (bajo `/api/me`), no hay
 * `alumnoId` implícito del principal: el entrenador consulta sobre otros, acotado a sus propios grupos por
 * el propio caso de uso.
 */
@RestController
@RequestMapping("/api/alertas")
class CoachAlertController(
    private val listAlerts: ListCoachAlertsQuery,
    private val principalProvider: PrincipalProvider,
) {
    /** GET /api/alertas?grupoId= — alertas activas de los grupos del entrenador (todos si se omite `grupoId`). */
    @GetMapping
    @Authorize("COACH_ALERT:LIST")
    fun list(
        @RequestParam(required = false) grupoId: UUID?,
    ): ResponseEntity<*> =
        listAlerts.execute(principalProvider.current(), grupoId).fold(
            { error -> error.toErrorResponse() },
            { result -> ResponseEntity.ok(result.toResponse()) },
        )
}
