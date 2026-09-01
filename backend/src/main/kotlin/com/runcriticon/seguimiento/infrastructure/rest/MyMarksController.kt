package com.runcriticon.seguimiento.infrastructure.rest

import com.runcriticon.seguimiento.application.usecases.marks.GetMyMarksQuery
import com.runcriticon.seguimiento.application.usecases.marks.RecordMarkCommand
import com.runcriticon.seguimiento.application.usecases.marks.WithdrawMarkCommand
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toRaceDistanceOrNull
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.api.rest.ErrorResponse
import com.runcriticon.shared.api.rest.MiMarcaRequest
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Las propias marcas del alumno (LAL-31). Fichero separado de [MyPlanController]: un caso de uso claro por
 * controller, mismo criterio que la separación entre `MyPlanController` y el resto de endpoints `/me` de
 * otros módulos. `alumnoId` nunca es un parámetro: sale siempre de [PrincipalProvider.current] — privacidad
 * fuerte (ADR-0002 D7), ni el entrenador ni el admin tienen ningún endpoint equivalente.
 */
@RestController
@RequestMapping("/api/me")
class MyMarksController(
    private val getMyMarks: GetMyMarksQuery,
    private val recordMark: RecordMarkCommand,
    private val withdrawMark: WithdrawMarkCommand,
    private val principalProvider: PrincipalProvider,
) {
    @GetMapping("/marcas")
    @Authorize("MARCA:LIST")
    fun myMarks(): ResponseEntity<*> =
        getMyMarks.execute(principalProvider.current()).fold(
            { error -> error.toErrorResponse() },
            { marks -> ResponseEntity.ok(marks.toResponse()) },
        )

    /** PUT /api/me/marcas/{distancia} — envío idempotente: crea la marca la primera vez, la sobreescribe
     * después (LAL-31, sin histórico). */
    @PutMapping("/marcas/{distancia}")
    @Authorize("MARCA:RECORD")
    fun recordMark(
        @PathVariable distancia: String,
        @RequestBody body: MiMarcaRequest,
    ): ResponseEntity<*> {
        val distance = distancia.toRaceDistanceOrNull() ?: return distanciaInvalida()
        return recordMark
            .execute(principalProvider.current(), distance, body.tiempoSegundos)
            .fold(
                { error -> error.toErrorResponse() },
                { mark -> ResponseEntity.ok(mark.toResponse()) },
            )
    }

    /** DELETE /api/me/marcas/{distancia} — idempotente: 204 tanto si borró una marca como si no había
     * ninguna (LAL-31 AC3). */
    @DeleteMapping("/marcas/{distancia}")
    @Authorize("MARCA:WITHDRAW")
    fun withdrawMark(
        @PathVariable distancia: String,
    ): ResponseEntity<*> {
        val distance = distancia.toRaceDistanceOrNull() ?: return distanciaInvalida()
        return withdrawMark
            .execute(principalProvider.current(), distance)
            .fold(
                { error -> error.toErrorResponse() },
                { ResponseEntity.noContent().build<Unit>() },
            )
    }

    private fun distanciaInvalida(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(code = "INVALID_INPUT", field = "distancia", message = "Revisa los datos introducidos"),
        )
}
