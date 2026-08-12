package com.runcriticon.clubtaxonomia.infrastructure.rest

import com.runcriticon.clubtaxonomia.application.usecases.coaches.ListCoachWorkloadQuery
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Listado de entrenadores del club con su carga. Solo ADMIN.
 *
 * **Cuelga de `/api/entrenadores/resumen`, no de `/api/entrenadores`** (que ya sirve
 * `identidad.infrastructure.rest.CoachController` — la gestión de sesión del entrenador, LAL-7/LAL-13): dos
 * controllers no pueden colgar de la misma ruta, y esta es una vista distinta (la de club, sobre la proyección
 * local), no un cruce de módulos — mismo criterio que ya documenta `StudentDirectoryController` para `/alumnos`.
 *
 * **No se llama `CoachController`** por el mismo motivo por el que el caso de uso no se llama `ListCoachesQuery`:
 * Spring nombra los beans por el simple class name, sin distinguir paquete, y `identidad.CoachController` ya existe.
 */
@RestController
@RequestMapping("/api/entrenadores/resumen")
class CoachDirectoryController(
    private val listCoachWorkload: ListCoachWorkloadQuery,
    private val principalProvider: PrincipalProvider,
) {
    @GetMapping
    @Authorize("COACH:LIST")
    fun list(): ResponseEntity<*> =
        listCoachWorkload.execute(principalProvider.current()).fold(
            { error -> error.toErrorResponse() },
            { coaches -> ResponseEntity.ok(coaches.toResponse()) },
        )
}
