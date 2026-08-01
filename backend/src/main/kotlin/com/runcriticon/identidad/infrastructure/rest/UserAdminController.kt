package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.account.DeactivateUserCommand
import com.runcriticon.identidad.application.usecases.account.DeleteUserCommand
import com.runcriticon.identidad.application.usecases.session.RevokeUserSessionsCommand
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.infrastructure.rest.mappers.toErrorResponse
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Endpoints de gestión de usuarios por admin. La autorización RBAC la resuelve cada caso de uso mediante la
 * [com.runcriticon.shared.autorizacion.AuthorizationMatrix]; el controlador solo obtiene el principal y traduce el
 * error a HTTP ([ErrorMapper]). Las tres acciones responden 204; las dos primeras son
 * idempotentes-a-efectos-de-cliente, la supresión no: repetirla devuelve 404 porque el recurso ya no existe.
 */
@RestController
@RequestMapping("/api/usuarios")
class UserAdminController(
    private val revokeUserSessions: RevokeUserSessionsCommand,
    private val deactivateUser: DeactivateUserCommand,
    private val deleteUser: DeleteUserCommand,
    private val principalProvider: PrincipalProvider,
) {
    /** POST /api/usuarios/{id}/revocacion-sesiones — revoca todas las sesiones activas del usuario. */
    @PostMapping("/{id}/revocacion-sesiones")
    @Authorize("USER:REVOKE_SESSIONS")
    fun revokeSessions(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        revokeUserSessions.execute(principalProvider.current(), UserId.of(id)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )

    /** POST /api/usuarios/{id}/desactivacion — desactiva la cuenta y cierra sus sesiones. */
    @PostMapping("/{id}/desactivacion")
    @Authorize("USER:DEACTIVATE")
    fun deactivate(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        deactivateUser.execute(principalProvider.current(), UserId.of(id)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )

    /**
     * DELETE /api/usuarios/{id} — elimina a la persona y sus datos personales (derecho de supresión).
     *
     * `DELETE` sobre el recurso, y no un `POST /{id}/supresion` como las dos acciones de arriba, porque aquí el recurso
     * identificado por la URL desaparece. Repetir la llamada devuelve 404, no 204: el usuario ya no existe.
     */
    @DeleteMapping("/{id}")
    @Authorize("USER:DELETE")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        deleteUser.execute(principalProvider.current(), UserId.of(id)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )
}
