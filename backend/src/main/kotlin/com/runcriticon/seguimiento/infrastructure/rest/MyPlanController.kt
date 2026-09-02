package com.runcriticon.seguimiento.infrastructure.rest

import com.runcriticon.seguimiento.application.usecases.adjustment.RescheduleDayCommand
import com.runcriticon.seguimiento.application.usecases.adjustment.WithdrawDayAdjustmentCommand
import com.runcriticon.seguimiento.application.usecases.plan.GetMyWeekQuery
import com.runcriticon.seguimiento.application.usecases.report.SubmitSessionReportCommand
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toDomain
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.api.rest.MiReajusteRequest
import com.runcriticon.shared.api.rest.MiReporteRequest
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * La propia semana resuelta del alumno (LAL-29), su reporte de sesión (LAL-30) y su reajuste de día (LAL-33).
 * `alumnoId` nunca es un parámetro: sale siempre de [PrincipalProvider.current] — mismo criterio de
 * `/me/permissions`, no el de los controllers de `club_taxonomia`/`planificacion`, que sí reciben ids ajenos
 * porque los opera el entrenador o el admin.
 */
@RestController
@RequestMapping("/api/me")
class MyPlanController(
    private val getMyWeek: GetMyWeekQuery,
    private val submitSessionReport: SubmitSessionReportCommand,
    private val rescheduleDay: RescheduleDayCommand,
    private val withdrawDayAdjustment: WithdrawDayAdjustmentCommand,
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

    /** PUT /api/me/reportes/{dia} — envío idempotente: crea el reporte la primera vez, lo reemplaza después. */
    @PutMapping("/reportes/{dia}")
    @Authorize("SESSION_REPORT:SUBMIT")
    fun submitReport(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dia: LocalDate,
        @RequestBody body: MiReporteRequest,
    ): ResponseEntity<*> =
        submitSessionReport
            .execute(
                actor = principalProvider.current(),
                day = dia,
                status = body.estado.toDomain(),
                rating = body.valoracion,
                reason = body.motivo?.toDomain(),
                notes = body.notas,
            ).fold(
                { error -> error.toErrorResponse() },
                { resolved -> ResponseEntity.ok(resolved.toResponse()) },
            )

    /** PUT /api/me/reajustes/{dia} — envío idempotente: crea el reajuste la primera vez, lo reemplaza después
     * (LAL-33). [dia] es el día EFECTIVO de la sesión de origen. */
    @PutMapping("/reajustes/{dia}")
    @Authorize("DAY_ADJUSTMENT:RESCHEDULE")
    fun reschedule(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dia: LocalDate,
        @RequestBody body: MiReajusteRequest,
    ): ResponseEntity<*> =
        rescheduleDay
            .execute(
                actor = principalProvider.current(),
                day = dia,
                action = body.accion.toDomain(),
                targetDay = body.diaDestino,
                reason = body.motivo.toDomain(),
                message = body.mensaje,
                conflictResolution = body.resolucionConflicto?.toDomain(),
            ).fold(
                { error -> error.toErrorResponse() },
                { adjustment -> ResponseEntity.ok(adjustment.toResponse()) },
            )

    /** DELETE /api/me/reajustes/{dia} — idempotente: 204 tanto si deshizo un reajuste como si no había
     * ninguno (LAL-33). */
    @DeleteMapping("/reajustes/{dia}")
    @Authorize("DAY_ADJUSTMENT:WITHDRAW")
    fun withdrawReajuste(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dia: LocalDate,
    ): ResponseEntity<*> =
        withdrawDayAdjustment
            .execute(principalProvider.current(), dia)
            .fold(
                { error -> error.toErrorResponse() },
                { ResponseEntity.noContent().build<Unit>() },
            )
}
