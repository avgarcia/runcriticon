package com.runcriticon.clubtaxonomia.infrastructure.rest

import com.runcriticon.clubtaxonomia.application.usecases.groups.CreateGroupCommand
import com.runcriticon.clubtaxonomia.application.usecases.groups.PreviewGroupMembersQuery
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.api.rest.CreateGroupRequest
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Grupos del club: consultas nombradas sobre tags. ADMIN y ENTRENADOR.
 *
 * `miembros` cuelga de la colección y no de un `{grupoId}` a propósito: la previsualización sirve justo para cuando
 * el grupo todavía no existe, así que no hay id del que colgarla. Es un segmento literal, de modo que un futuro
 * `/grupos/{grupoId}` no compite con él.
 *
 * El filtro viaja como `tagValueId` repetido en vez de en un body: previsualizar es una lectura, no crea nada, y una
 * lista de ids cabe de sobra en la URL.
 */
@RestController
@RequestMapping("/api/grupos")
class GroupController(
    private val createGroup: CreateGroupCommand,
    private val previewGroupMembers: PreviewGroupMembersQuery,
    private val principalProvider: PrincipalProvider,
) {
    /** POST /api/grupos — crea un grupo con el filtro de tags que define su membresía. */
    @PostMapping
    @Authorize("GROUP:CREATE")
    fun create(
        @RequestBody req: CreateGroupRequest,
    ): ResponseEntity<*> =
        createGroup.execute(principalProvider.current(), req.nombre, req.valores).fold(
            { error -> error.toErrorResponse() },
            { group -> ResponseEntity.status(HttpStatus.CREATED).body(group.toResponse()) },
        )

    /** GET /api/grupos/miembros?tagValueId=a&tagValueId=b — alumnos que cumplirían ese filtro, sin guardar nada. */
    @GetMapping("/miembros")
    @Authorize("GROUP:LIST")
    fun previewMembers(
        @RequestParam(name = "tagValueId", required = false) tagValueId: List<UUID>?,
    ): ResponseEntity<*> =
        previewGroupMembers.execute(principalProvider.current(), tagValueId.orEmpty()).fold(
            { error -> error.toErrorResponse() },
            { members -> ResponseEntity.ok(members.toResponse()) },
        )
}
