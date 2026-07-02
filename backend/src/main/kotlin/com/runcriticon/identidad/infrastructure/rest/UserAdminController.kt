package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.DeactivateUser
import com.runcriticon.identidad.application.usecases.RevokeUserSessions
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.PrincipalProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Endpoints de gestión de usuarios por admin (ADR-0003 D11, ADR-0009 D12). La autorización RBAC la
 * resuelve cada caso de uso mediante la [com.runcriticon.shared.autorizacion.AuthorizationMatrix]; el
 * controlador solo obtiene el principal y traduce el error a HTTP ([ErrorMapper]). Ambas acciones son
 * idempotentes-a-efectos-de-cliente y responden 204.
 */
@RestController
@RequestMapping("/api/usuarios")
class UserAdminController(
    private val revokeUserSessions: RevokeUserSessions,
    private val deactivateUser: DeactivateUser,
    private val principalProvider: PrincipalProvider,
) {
    /** POST /api/usuarios/{id}/revocacion-sesiones — revoca todas las sesiones activas del usuario. */
    @PostMapping("/{id}/revocacion-sesiones")
    fun revokeSessions(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        revokeUserSessions.execute(principalProvider.current(), UserId.of(id)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )

    /** POST /api/usuarios/{id}/desactivacion — desactiva la cuenta y cierra sus sesiones. */
    @PostMapping("/{id}/desactivacion")
    fun deactivate(
        @PathVariable id: UUID,
    ): ResponseEntity<*> =
        deactivateUser.execute(principalProvider.current(), UserId.of(id)).fold(
            { error -> error.toErrorResponse() },
            { ResponseEntity.noContent().build<Void>() },
        )
}
