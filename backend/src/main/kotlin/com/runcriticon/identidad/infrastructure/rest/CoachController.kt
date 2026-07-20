package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.coach.ListCoachesQuery
import com.runcriticon.identidad.application.usecases.invitation.InviteCoachCommand
import com.runcriticon.identidad.application.usecases.invitation.ResendInvitationCommand
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.identidad.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import com.runcriticon.identidad.application.usecases.coach.CoachSummary as CoachSummaryDto

/**
 * Endpoints de gestión de entrenadores. La autorización RBAC la resuelve cada caso de uso mediante la
 * [AuthorizationMatrix].
 */
@RestController
@RequestMapping("/api/entrenadores")
class CoachController(
    private val inviteCoach: InviteCoachCommand,
    private val listCoaches: ListCoachesQuery,
    private val resendInvitation: ResendInvitationCommand,
    private val principalProvider: PrincipalProvider,
) {
    /** GET /api/entrenadores — lista los entrenadores del club. */
    @GetMapping
    @Authorize("COACH:LIST")
    fun list(): ResponseEntity<*> =
        listCoaches.execute(principalProvider.current()).fold(
            { error -> error.toErrorResponse() },
            { coaches -> ResponseEntity.ok(coaches.map { it.toResponse() }) },
        )

    /** POST /api/entrenadores — da de alta un entrenador y le envía la invitación por email. */
    @PostMapping
    @Authorize("COACH:INVITE")
    fun invite(
        @RequestBody req: InviteCoachRequest,
    ): ResponseEntity<*> =
        inviteCoach.execute(principalProvider.current(), req.nombre, req.email).fold(
            { error -> error.toErrorResponse() },
            { userId -> ResponseEntity.status(HttpStatus.CREATED).body(InviteCoachResponse(userId.value)) },
        )

    /** POST /api/entrenadores/{id}/invitaciones — emite una nueva invitación (invalida la anterior). */
    @PostMapping("/{id}/invitaciones")
    @Authorize("COACH:INVITE")
    fun resend(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        resendInvitation.execute(principalProvider.current(), UserId.of(id)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )
}

/** Mapea el DTO de aplicación al modelo del contrato. */
private fun CoachSummaryDto.toResponse(): CoachSummary =
    CoachSummary(
        id = id,
        nombre = name,
        email = email,
        estado =
            when (status) {
                UserStatus.INVITADO -> CoachSummary.Estado.INVITADO
                UserStatus.ACTIVO -> CoachSummary.Estado.ACTIVO
                UserStatus.DESACTIVADO -> CoachSummary.Estado.DESACTIVADO
            },
    )
