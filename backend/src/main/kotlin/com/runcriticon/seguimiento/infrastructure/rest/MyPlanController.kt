package com.runcriticon.seguimiento.infrastructure.rest

import com.runcriticon.seguimiento.application.usecases.plan.GetMyWeekQuery
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * La propia semana resuelta del alumno (LAL-29). `alumnoId` nunca es un parámetro: sale siempre de
 * [PrincipalProvider.current] — mismo criterio de `/me/permissions`, no el de los controllers de
 * `club_taxonomia`/`planificacion`, que sí reciben ids ajenos porque los opera el entrenador o el admin.
 */
@RestController
@RequestMapping("/api/me")
class MyPlanController(
    private val getMyWeek: GetMyWeekQuery,
    private val principalProvider: PrincipalProvider,
) {
    /** GET /api/me/plan?semana=YYYY-MM-DD — la semana pedida (o la actual) ya resuelta, sin resolver nada aquí. */
    @GetMapping("/plan")
    @Authorize("RESOLVED_SESSION:LIST")
    fun myWeek(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) semana: LocalDate?,
    ): ResponseEntity<*> =
        getMyWeek.execute(principalProvider.current(), semana).fold(
            { error -> error.toErrorResponse() },
            { result -> ResponseEntity.ok(result.toResponse()) },
        )
}
