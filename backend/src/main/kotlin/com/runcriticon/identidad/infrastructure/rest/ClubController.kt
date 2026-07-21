package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.club.QueryClubQuery
import com.runcriticon.identidad.application.usecases.club.UpdateClubCommand
import com.runcriticon.identidad.domain.club.Club
import com.runcriticon.identidad.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.AuthenticatedOnly
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoints de la ficha del club. Opera siempre sobre el club del principal (`actor.clubId`) — nunca
 * sobre un id recibido del cliente, por eso no hay `{id}` en la ruta.
 */
@RestController
@RequestMapping("/api/club")
class ClubController(
    private val queryClub: QueryClubQuery,
    private val updateClub: UpdateClubCommand,
    private val principalProvider: PrincipalProvider,
) {
    /** GET /api/club — ficha del propio club. Cualquier rol autenticado puede consultarla. */
    @GetMapping
    @AuthenticatedOnly(
        "Devuelve la ficha del propio club del principal; no hay recurso de terceros que autorizar",
    )
    fun get(): ResponseEntity<*> =
        queryClub.execute(principalProvider.current()).fold(
            { error -> error.toErrorResponse() },
            { club -> ResponseEntity.ok(club.toResponse()) },
        )

    /** PATCH /api/club — cambia el nombre del club. Solo el ADMIN. */
    @PatchMapping
    @Authorize("CLUB:UPDATE")
    fun update(
        @RequestBody req: ClubPatchRequest,
    ): ResponseEntity<*> =
        updateClub.execute(principalProvider.current(), req.nombre).fold(
            { error -> error.toErrorResponse() },
            { club -> ResponseEntity.ok(club.toResponse()) },
        )
}

/** Mapea el agregado de dominio al modelo del contrato. */
private fun Club.toResponse(): ClubResponse =
    ClubResponse(
        id = id.value,
        nombre = name,
        slug = slug,
    )
