package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.InviteStudent
import com.runcriticon.identidad.application.usecases.ResendStudentInvitation
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Endpoints de gestión de alumnos (ADR-0001 D10, ADR-0009 D12). El alta y la reinvitación las pueden
 * ejecutar admin y entrenador (delegación, ADR-0003 D3); la autorización RBAC la resuelve cada caso de
 * uso mediante la [com.runcriticon.shared.autorizacion.AuthorizationMatrix].
 */
@RestController
@RequestMapping("/api/alumnos")
class StudentController(
    private val inviteStudent: InviteStudent,
    private val resendStudentInvitation: ResendStudentInvitation,
    private val principalProvider: PrincipalProvider,
) {
    /** POST /api/alumnos — da de alta un alumno y le envía la invitación por email. */
    @PostMapping
    @Authorize("STUDENT:INVITE")
    fun invite(
        @RequestBody req: InviteStudentRequest,
    ): ResponseEntity<*> =
        inviteStudent.execute(principalProvider.current(), req.nombre, req.email).fold(
            { error -> error.toErrorResponse() },
            { userId -> ResponseEntity.status(HttpStatus.CREATED).body(InviteStudentResponse(userId.value)) },
        )

    /** POST /api/alumnos/{id}/invitaciones — emite una nueva invitación (invalida la anterior). */
    @PostMapping("/{id}/invitaciones")
    @Authorize("STUDENT:INVITE")
    fun resend(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        resendStudentInvitation.execute(principalProvider.current(), UserId.of(id)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )
}
