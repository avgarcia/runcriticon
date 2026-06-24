package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.InviteStudent
import com.runcriticon.shared.autorizacion.PrincipalProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Endpoints de gestión de alumnos (ADR-0001 D10, ADR-0009 D12). El alta la pueden ejecutar admin y
 * entrenador (delegación, ADR-0003 D3); la autorización RBAC la resuelve el caso de uso mediante la
 * [com.runcriticon.shared.autorizacion.AuthorizationMatrix].
 */
@RestController
@RequestMapping("/api/alumnos")
class StudentController(
    private val inviteStudent: InviteStudent,
    private val principalProvider: PrincipalProvider,
) {
    /** POST /api/alumnos — da de alta un alumno y le envía la invitación por email. */
    @PostMapping
    fun invite(
        @RequestBody req: InviteStudentRequest,
    ): ResponseEntity<*> =
        inviteStudent.execute(principalProvider.current(), req.nombre, req.email).fold(
            { error -> error.toErrorResponse() },
            { userId -> ResponseEntity.status(HttpStatus.CREATED).body(InviteStudentResponse(userId.value)) },
        )
}
