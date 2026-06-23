package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.InviteCoach
import com.runcriticon.identidad.application.usecases.ResendInvitation
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.PrincipalProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Endpoints de gestión de entrenadores (ADR-0001 D10, ADR-0009 D12).
 * La autorización RBAC la resuelve cada caso de uso mediante la [AuthorizationMatrix].
 */
@RestController
@RequestMapping("/api/entrenadores")
class CoachController(
    private val inviteCoach: InviteCoach,
    private val resendInvitation: ResendInvitation,
    private val principalProvider: PrincipalProvider,
) {
    /** POST /api/entrenadores — da de alta un entrenador y le envía la invitación por email. */
    @PostMapping
    fun invite(
        @RequestBody req: InviteCoachRequest,
    ): ResponseEntity<*> =
        inviteCoach.execute(principalProvider.current(), req.name, req.email).fold(
            { error -> error.toErrorResponse() },
            { userId -> ResponseEntity.status(HttpStatus.CREATED).body(InviteCoachResponse(userId.value.toString())) },
        )

    /** POST /api/entrenadores/{id}/invitaciones — emite una nueva invitación (invalida la anterior). */
    @PostMapping("/{id}/invitaciones")
    fun resend(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        resendInvitation.execute(principalProvider.current(), UserId.of(id)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )
}
