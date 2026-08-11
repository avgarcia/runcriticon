package com.runcriticon.clubtaxonomia.infrastructure.rest

import com.runcriticon.clubtaxonomia.application.usecases.students.ListStudentsQuery
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Listado de alumnos del club, con su clasificación. ADMIN y ENTRENADOR.
 *
 * **Cuelga de `/api/alumnos` aunque el alta de alumno la maneje `identidad`, y no es un cruce de módulos** — mismo
 * criterio que ya documenta `StudentTagController` para la clasificación: el recurso que se manipula aquí (la lectura
 * de la proyección local con sus tags) es de este módulo, la URL solo dice de quién es.
 *
 * **No se llama `StudentController`** pese a ser el nombre natural: `identidad.infrastructure.rest.StudentController`
 * ya existe (el alta de alumno) y Spring nombra los beans por el simple class name, sin distinguir paquete -- dos
 * clases con el mismo nombre chocan al arrancar el contexto aunque no haya ningún cruce real de módulos.
 */
@RestController
@RequestMapping("/api/alumnos")
class StudentDirectoryController(
    private val listStudents: ListStudentsQuery,
    private val principalProvider: PrincipalProvider,
) {
    /** GET /api/alumnos?tagValueId=a&tagValueId=b — alumnos del club con sus tags, filtrados en AND. */
    @GetMapping
    @Authorize("STUDENT:LIST")
    fun list(
        @RequestParam(name = "tagValueId", required = false) tagValueId: List<UUID>?,
    ): ResponseEntity<*> =
        listStudents.execute(principalProvider.current(), tagValueId.orEmpty()).fold(
            { error -> error.toErrorResponse() },
            { students -> ResponseEntity.ok(students.toResponse()) },
        )
}
