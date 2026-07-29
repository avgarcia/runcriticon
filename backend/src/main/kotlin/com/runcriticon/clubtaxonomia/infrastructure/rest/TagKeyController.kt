package com.runcriticon.clubtaxonomia.infrastructure.rest

import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.AddTagValueCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ArchiveTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.CreateTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.ReactivateTagKeyCommand
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.RenameTagKeyCommand
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.api.rest.TagKeyLabelRequest
import com.runcriticon.shared.api.rest.TagValueLabelRequest
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Escritura sobre los ejes de la taxonomía. Todo aquí es `TAXONOMY:MANAGE` (solo ADMIN).
 *
 * El alta de un valor cuelga de este controller y no de [TagValueController] porque `valores` es un sub-recurso del
 * eje: es el mismo patrón que la reemisión de invitaciones bajo `/entrenadores/{id}/invitaciones`.
 *
 * `archivados` es una colección y archivar es pertenecer a ella. No hay ambigüedad de enrutado con `/{tagId}`: son
 * profundidades distintas (`/tags/{tagId}` frente a `/tags/archivados/{tagId}`).
 */
@RestController
@RequestMapping("/api/taxonomia/tags")
class TagKeyController(
    private val createTagKey: CreateTagKeyCommand,
    private val renameTagKey: RenameTagKeyCommand,
    private val archiveTagKey: ArchiveTagKeyCommand,
    private val reactivateTagKey: ReactivateTagKeyCommand,
    private val addTagValue: AddTagValueCommand,
    private val principalProvider: PrincipalProvider,
) {
    /** POST /api/taxonomia/tags — crea un eje. */
    @PostMapping
    @Authorize("TAXONOMY:MANAGE")
    fun create(
        @RequestBody req: TagKeyLabelRequest,
    ): ResponseEntity<*> =
        createTagKey.execute(principalProvider.current(), req.nombre).fold(
            { error -> error.toErrorResponse() },
            { key -> ResponseEntity.status(HttpStatus.CREATED).body(key.toResponse()) },
        )

    /** PATCH /api/taxonomia/tags/{tagId} — renombra un eje. */
    @PatchMapping("/{tagId}")
    @Authorize("TAXONOMY:MANAGE")
    fun rename(
        @PathVariable tagId: UUID,
        @RequestBody req: TagKeyLabelRequest,
    ): ResponseEntity<*> =
        renameTagKey.execute(principalProvider.current(), tagId, req.nombre).fold(
            { error -> error.toErrorResponse() },
            { key -> ResponseEntity.ok(key.toResponse()) },
        )

    /** PUT /api/taxonomia/tags/archivados/{tagId} — mete el eje en la colección de archivados. */
    @PutMapping("/archivados/{tagId}")
    @Authorize("TAXONOMY:MANAGE")
    fun archive(
        @PathVariable tagId: UUID,
    ): ResponseEntity<*> =
        archiveTagKey.execute(principalProvider.current(), tagId).fold(
            { error -> error.toErrorResponse() },
            { key -> ResponseEntity.ok(key.toResponse()) },
        )

    /** DELETE /api/taxonomia/tags/archivados/{tagId} — lo saca de ella (idempotente). */
    @DeleteMapping("/archivados/{tagId}")
    @Authorize("TAXONOMY:MANAGE")
    fun reactivate(
        @PathVariable tagId: UUID,
    ): ResponseEntity<*> =
        reactivateTagKey.execute(principalProvider.current(), tagId).fold(
            { error -> error.toErrorResponse() },
            { key -> ResponseEntity.ok(key.toResponse()) },
        )

    /** POST /api/taxonomia/tags/{tagId}/valores — añade un valor al eje. */
    @PostMapping("/{tagId}/valores")
    @Authorize("TAXONOMY:MANAGE")
    fun addValue(
        @PathVariable tagId: UUID,
        @RequestBody req: TagValueLabelRequest,
    ): ResponseEntity<*> =
        addTagValue.execute(principalProvider.current(), tagId, req.valor).fold(
            { error -> error.toErrorResponse() },
            { value -> ResponseEntity.status(HttpStatus.CREATED).body(value.toResponse()) },
        )
}
