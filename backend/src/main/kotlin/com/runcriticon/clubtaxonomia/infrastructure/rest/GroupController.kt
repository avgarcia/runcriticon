package com.runcriticon.clubtaxonomia.infrastructure.rest

import com.runcriticon.clubtaxonomia.application.usecases.groups.AssignCoachToGroupCommand
import com.runcriticon.clubtaxonomia.application.usecases.groups.ClearGroupMembershipOverrideCommand
import com.runcriticon.clubtaxonomia.application.usecases.groups.CreateGroupCommand
import com.runcriticon.clubtaxonomia.application.usecases.groups.GetGroupDetailQuery
import com.runcriticon.clubtaxonomia.application.usecases.groups.ListGroupCoachesQuery
import com.runcriticon.clubtaxonomia.application.usecases.groups.ListGroupsQuery
import com.runcriticon.clubtaxonomia.application.usecases.groups.OverrideGroupMembershipCommand
import com.runcriticon.clubtaxonomia.application.usecases.groups.PreviewGroupMembersQuery
import com.runcriticon.clubtaxonomia.application.usecases.groups.UnassignCoachFromGroupCommand
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.clubtaxonomia.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.api.rest.CreateGroupRequest
import com.runcriticon.shared.api.rest.GroupOverrideRequest
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Grupos del club: consultas nombradas sobre tags. ADMIN y ENTRENADOR.
 *
 * `miembros` cuelga de la colección y no de un `{grupoId}` a propósito: la previsualización sirve justo para cuando
 * el grupo todavía no existe, así que no hay id del que colgarla. Es un segmento literal y por eso convive con
 * `/grupos/{grupoId}`: Spring resuelve antes el literal que la plantilla.
 *
 * Las excepciones manuales cuelgan de `overrides` y no de `alumnos`: lo que se escribe no es la pertenencia —esa se
 * calcula— sino la excepción que la sobrescribe, y así `alumnos` queda libre para lo que sí es una lista de gente.
 *
 * El filtro viaja como `tagValueId` repetido en vez de en un body: previsualizar es una lectura, no crea nada, y una
 * lista de ids cabe de sobra en la URL.
 */
@RestController
@RequestMapping("/api/grupos")
class GroupController(
    private val listGroups: ListGroupsQuery,
    private val createGroup: CreateGroupCommand,
    private val previewGroupMembers: PreviewGroupMembersQuery,
    private val getGroupDetail: GetGroupDetailQuery,
    private val overrideMembership: OverrideGroupMembershipCommand,
    private val clearMembershipOverride: ClearGroupMembershipOverrideCommand,
    private val listGroupCoaches: ListGroupCoachesQuery,
    private val assignCoach: AssignCoachToGroupCommand,
    private val unassignCoach: UnassignCoachFromGroupCommand,
    private val principalProvider: PrincipalProvider,
) {
    /** GET /api/grupos — grupos del club con su filtro y cuántos alumnos caen en cada uno. */
    @GetMapping
    @Authorize("GROUP:LIST")
    fun list(): ResponseEntity<*> =
        listGroups.execute(principalProvider.current()).fold(
            { error -> error.toErrorResponse() },
            { groups -> ResponseEntity.ok(groups.toResponse()) },
        )

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

    /** GET /api/grupos/{grupoId} — el grupo con sus miembros, el motivo de cada uno y sus exclusiones manuales. */
    @GetMapping("/{grupoId}")
    @Authorize("GROUP:LIST")
    fun detail(
        @PathVariable grupoId: UUID,
    ): ResponseEntity<*> =
        getGroupDetail.execute(principalProvider.current(), grupoId).fold(
            { error -> error.toErrorResponse() },
            { detail -> ResponseEntity.ok(detail.toResponse()) },
        )

    /**
     * PUT /api/grupos/{grupoId}/overrides/{alumnoId} — mete o saca al alumno a mano.
     *
     * Devuelve el detalle ya recalculado en vez de un 204: la pantalla necesita el nuevo recuento y el nuevo origen de
     * cada miembro, y pedirlos aparte sería una segunda lectura fuera de la transacción que acaba de escribir.
     */
    @PutMapping("/{grupoId}/overrides/{alumnoId}")
    @Authorize("GROUP:UPDATE")
    fun setOverride(
        @PathVariable grupoId: UUID,
        @PathVariable alumnoId: UUID,
        @RequestBody req: GroupOverrideRequest,
    ): ResponseEntity<*> =
        overrideMembership.execute(principalProvider.current(), grupoId, alumnoId, req.incluido).fold(
            { error -> error.toErrorResponse() },
            { detail -> ResponseEntity.ok(detail.toResponse()) },
        )

    /**
     * DELETE /api/grupos/{grupoId}/overrides/{alumnoId} — quita la excepción y devuelve al alumno al filtro.
     *
     * 204 tanto si había excepción como si no: la operación es idempotente y no hay motivo para contarle al cliente si
     * existía la fila. El 404 queda reservado a la frontera de club, es decir, al grupo.
     */
    @DeleteMapping("/{grupoId}/overrides/{alumnoId}")
    @Authorize("GROUP:UPDATE")
    fun clearOverride(
        @PathVariable grupoId: UUID,
        @PathVariable alumnoId: UUID,
    ): ResponseEntity<*> =
        clearMembershipOverride.execute(principalProvider.current(), grupoId, alumnoId).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Unit>() },
        )

    /** GET /api/grupos/{grupoId}/entrenadores — entrenadores asignados al grupo. */
    @GetMapping("/{grupoId}/entrenadores")
    @Authorize("GROUP:LIST")
    fun coaches(
        @PathVariable grupoId: UUID,
    ): ResponseEntity<*> =
        listGroupCoaches.execute(principalProvider.current(), grupoId).fold(
            { error -> error.toErrorResponse() },
            { coaches -> ResponseEntity.ok(coaches.toResponse()) },
        )

    /**
     * PUT /api/grupos/{grupoId}/entrenadores/{entrenadorId} — vincula al entrenador con el grupo. Solo ADMIN.
     *
     * Devuelve la lista ya recalculada, mismo criterio que [setOverride] devuelve el detalle recalculado.
     */
    @PutMapping("/{grupoId}/entrenadores/{entrenadorId}")
    @Authorize("GROUP:ASSIGN_COACH")
    fun assignCoach(
        @PathVariable grupoId: UUID,
        @PathVariable entrenadorId: UUID,
    ): ResponseEntity<*> =
        assignCoach.execute(principalProvider.current(), grupoId, entrenadorId).fold(
            { error -> error.toErrorResponse() },
            { coaches -> ResponseEntity.ok(coaches.toResponse()) },
        )

    /**
     * DELETE /api/grupos/{grupoId}/entrenadores/{entrenadorId} — desvincula al entrenador del grupo. Solo ADMIN.
     *
     * 204 tanto si estaba asignado como si no, mismo criterio idempotente que [clearOverride].
     */
    @DeleteMapping("/{grupoId}/entrenadores/{entrenadorId}")
    @Authorize("GROUP:ASSIGN_COACH")
    fun unassignCoach(
        @PathVariable grupoId: UUID,
        @PathVariable entrenadorId: UUID,
    ): ResponseEntity<*> =
        unassignCoach.execute(principalProvider.current(), grupoId, entrenadorId).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Unit>() },
        )
}
