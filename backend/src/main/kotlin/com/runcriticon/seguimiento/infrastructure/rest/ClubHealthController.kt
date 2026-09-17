package com.runcriticon.seguimiento.infrastructure.rest

import com.runcriticon.seguimiento.application.usecases.clubhealth.ListGroupActivityQuery
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.seguimiento.infrastructure.rest.mappers.toResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Vista de salud del club (admin): última actividad reportada por grupo. */
@RestController
@RequestMapping("/api/salud-del-club")
class ClubHealthController(
    private val listGroupActivity: ListGroupActivityQuery,
    private val principalProvider: PrincipalProvider,
) {
    @GetMapping("/actividad")
    @Authorize("CLUB_HEALTH:LIST")
    fun listActivity(): ResponseEntity<*> =
        listGroupActivity.execute(principalProvider.current()).fold(
            { error -> error.toErrorResponse() },
            { activity -> ResponseEntity.ok(activity.toResponse()) },
        )
}
