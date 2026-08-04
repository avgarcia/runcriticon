package com.runcriticon.clubtaxonomia.infrastructure.rest

import com.runcriticon.clubtaxonomia.application.usecases.studenttags.AssignStudentTagCommand
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.ListStudentTagsQuery
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.ReplaceStudentTagsCommand
import com.runcriticon.clubtaxonomia.application.usecases.studenttags.UnassignStudentTagCommand
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.api.rest.StudentTagAssignmentRequest
import com.runcriticon.shared.api.rest.StudentTagsRequest
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Clasificación de un alumno: qué valores de la taxonomía tiene asignados. ADMIN y ENTRENADOR.
 *
 * **Cuelga de `/api/alumnos` aunque el alumno sea un recurso de identidad, y no es un cruce de módulos.** El recurso
 * que se manipula aquí es la clasificación, que sí es de este módulo; la URL solo dice de quién es. No hay colisión
 * con el controlador de altas de identidad, que mapea otras rutas bajo el mismo prefijo, y ningún módulo importa
 * código del otro. En el contrato la frontera queda visible en el tag `clasificacion`, separado de `alumnos`.
 *
 * Las tres escrituras devuelven la clasificación **completa** resultante, no el valor tocado: así el cliente pinta los
 * chips con lo que responde el servidor en vez de recomponerlos por su cuenta. El desasignado es la excepción y
 * responde 204, porque quitar un chip no necesita repintar los demás.
 */
@RestController
@RequestMapping("/api/alumnos")
class StudentTagController(
    private val listStudentTags: ListStudentTagsQuery,
    private val replaceStudentTags: ReplaceStudentTagsCommand,
    private val assignStudentTag: AssignStudentTagCommand,
    private val unassignStudentTag: UnassignStudentTagCommand,
    private val principalProvider: PrincipalProvider,
) {
    /** GET /api/alumnos/{id}/tags — clasificación actual del alumno. */
    @GetMapping("/{id}/tags")
    @Authorize("STUDENT:CLASSIFY")
    fun list(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        listStudentTags.execute(principalProvider.current(), id).fold(
            { error -> error.toErrorResponse() },
            { tags -> ResponseEntity.ok(tags.toResponse()) },
        )

    /** PUT /api/alumnos/{id}/tags — deja al alumno exactamente con los valores indicados. */
    @PutMapping("/{id}/tags")
    @Authorize("STUDENT:CLASSIFY")
    fun replace(
        @PathVariable id: UUID,
        @RequestBody req: StudentTagsRequest,
    ): ResponseEntity<*> =
        replaceStudentTags.execute(principalProvider.current(), id, req.valores).fold(
            { error -> error.toErrorResponse() },
            { tags -> ResponseEntity.ok(tags.toResponse()) },
        )

    /** POST /api/alumnos/{id}/tags — añade un valor sin tocar el resto. */
    @PostMapping("/{id}/tags")
    @Authorize("STUDENT:CLASSIFY")
    fun assign(
        @PathVariable id: UUID,
        @RequestBody req: StudentTagAssignmentRequest,
    ): ResponseEntity<*> =
        assignStudentTag.execute(principalProvider.current(), id, req.valorId).fold(
            { error -> error.toErrorResponse() },
            { tags -> ResponseEntity.ok(tags.toResponse()) },
        )

    /** DELETE /api/alumnos/{id}/tags/{valorId} — quita un valor de la clasificación. */
    @DeleteMapping("/{id}/tags/{valorId}")
    @Authorize("STUDENT:CLASSIFY")
    fun unassign(
        @PathVariable id: UUID,
        @PathVariable valorId: UUID,
    ): ResponseEntity<*> =
        unassignStudentTag.execute(principalProvider.current(), id, valorId).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )
}
