package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.account.CancelUserDeletionCommand
import com.runcriticon.identidad.application.usecases.account.DeactivateUserCommand
import com.runcriticon.identidad.application.usecases.account.DeleteUserCommand
import com.runcriticon.identidad.application.usecases.account.MarkUserDeletionCommand
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
 * error a HTTP ([ErrorMapper]). Todas las acciones responden 204. Repetir la supresión devuelve 404, porque el recurso
 * ya no existe; repetir la desactivación, la baja o su cancelación devuelve 409, porque la cuenta ya no está en el
 * estado de partida.
 */
@RestController
@RequestMapping("/api/usuarios")
class UserAdminController(
    private val revokeUserSessions: RevokeUserSessionsCommand,
    private val deactivateUser: DeactivateUserCommand,
    private val deleteUser: DeleteUserCommand,
    private val markUserDeletion: MarkUserDeletionCommand,
    private val cancelUserDeletion: CancelUserDeletionCommand,
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

    /**
     * POST /api/usuarios/{id}/baja — supresión que decide el admin, con 30 días de gracia (ADR-0014 D14).
     *
     * La baja como sub-recurso de la cuenta, como `desactivacion`: `POST` la crea y `DELETE` la cancela. Exige
     * `USER:DELETE` porque es la supresión, con plazo.
     */
    @PostMapping("/{id}/baja")
    @Authorize("USER:DELETE")
    fun markDeletion(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        markUserDeletion.execute(principalProvider.current(), UserId.of(id)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )

    /** DELETE /api/usuarios/{id}/baja — cancela la baja mientras corre la gracia. */
    @DeleteMapping("/{id}/baja")
    @Authorize("USER:DELETE")
    fun cancelDeletion(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        cancelUserDeletion.execute(principalProvider.current(), UserId.of(id)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )
}
