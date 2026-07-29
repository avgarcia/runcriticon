package com.runcriticon.clubtaxonomia.infrastructure.rest

import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ArchiveTagValueCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ReactivateTagValueCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.RenameTagValueCommand
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.api.rest.TagValueLabelRequest
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Escritura sobre los valores de la taxonomía. Todo aquí es `TAXONOMY:MANAGE` (solo ADMIN).
 *
 * Las rutas no cuelgan del eje padre: el id de un valor es único en toda la taxonomía del club, y los casos de uso
 * solo reciben ese id. Anidarlas obligaría a validar una consistencia padre-hijo que no aporta nada. El alta sí va
 * anidada, en [TagKeyController], porque ahí el eje es un parámetro real de la operación.
 */
@RestController
@RequestMapping("/api/taxonomia/valores")
class TagValueController(
    private val renameTagValue: RenameTagValueCommand,
    private val archiveTagValue: ArchiveTagValueCommand,
    private val reactivateTagValue: ReactivateTagValueCommand,
    private val principalProvider: PrincipalProvider,
) {
    /** PATCH /api/taxonomia/valores/{valorId} — renombra un valor. */
    @PatchMapping("/{valorId}")
    @Authorize("TAXONOMY:MANAGE")
    fun rename(
        @PathVariable valorId: UUID,
        @RequestBody req: TagValueLabelRequest,
    ): ResponseEntity<*> =
        renameTagValue.execute(principalProvider.current(), valorId, req.valor).fold(
            { error -> error.toErrorResponse() },
            { value -> ResponseEntity.ok(value.toResponse()) },
        )

    /** PUT /api/taxonomia/valores/archivados/{valorId} — mete el valor en la colección de archivados. */
    @PutMapping("/archivados/{valorId}")
    @Authorize("TAXONOMY:MANAGE")
    fun archive(
        @PathVariable valorId: UUID,
    ): ResponseEntity<*> =
        archiveTagValue.execute(principalProvider.current(), valorId).fold(
            { error -> error.toErrorResponse() },
            { value -> ResponseEntity.ok(value.toResponse()) },
        )

    /** DELETE /api/taxonomia/valores/archivados/{valorId} — lo saca de ella (idempotente). */
    @DeleteMapping("/archivados/{valorId}")
    @Authorize("TAXONOMY:MANAGE")
    fun reactivate(
        @PathVariable valorId: UUID,
    ): ResponseEntity<*> =
        reactivateTagValue.execute(principalProvider.current(), valorId).fold(
            { error -> error.toErrorResponse() },
            { value -> ResponseEntity.ok(value.toResponse()) },
        )
}
