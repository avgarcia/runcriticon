package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.AuthenticateUser
import com.runcriticon.identidad.application.usecases.ChangeExpiredPassword
import com.runcriticon.identidad.application.usecases.LoginOutcome
import com.runcriticon.identidad.application.usecases.QueryCurrentSession
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.spring.SecuritySessionManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Endpoints de sesión (ADR-0003 D5, D7, D10, D11). En MVP mono-club el `clubId` es fijo (config); al
 * pasar a multi-club se inferirá del subdominio (ADR-0006 D16). El handler NO toca el contexto de
 * seguridad: delega en [SecuritySessionManager] (núcleo). El login fallido es 401 neutro (sin
 * distinguir email inexistente de contraseña incorrecta, ADR-0003 D5); la contraseña caducada es 409
 * con `code=PASSWORD_EXPIRED` y NO crea sesión: el cliente lleva al cambio forzado (ADR-0003 D7).
 */
@RestController
@RequestMapping("/api/sesion")
class SessionController(
    private val authenticateUser: AuthenticateUser,
    private val changeExpiredPassword: ChangeExpiredPassword,
    private val queryCurrentSession: QueryCurrentSession,
    private val sessionManager: SecuritySessionManager,
    @Value("\${runcriticon.bootstrap.club-id:00000000-0000-0000-0000-000000000001}")
    private val clubId: String,
) {
    @PostMapping
    @NoAuthRequired("Login público: punto de entrada de autenticación (ADR-0003 D5)")
    fun login(
        @RequestBody credenciales: CredentialsRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> =
        authenticateUser
            .execute(UUID.fromString(clubId), credenciales.email, credenciales.password)
            .fold(
                { ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>() },
                { outcome ->
                    when (outcome) {
                        is LoginOutcome.Authenticated -> {
                            sessionManager.startSession(outcome.principal, request, response)
                            ResponseEntity.ok(outcome.principal.toSessionResponse())
                        }

                        LoginOutcome.PasswordExpired ->
                            ResponseEntity.status(HttpStatus.CONFLICT).body(
                                ErrorResponse(
                                    code = "PASSWORD_EXPIRED",
                                    field = null,
                                    message = "Tu contraseña ha caducado; crea una nueva para continuar.",
                                ),
                            )
                    }
                },
            )

    @PostMapping("/contrasena")
    @NoAuthRequired("Cambio de contraseña caducada: revalida con la contraseña actual (ADR-0003 D7)")
    fun changePassword(
        @RequestBody req: PasswordChangeRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> =
        changeExpiredPassword
            .execute(UUID.fromString(clubId), req.email, req.currentPassword, req.newPassword)
            .fold(
                { error -> error.toErrorResponse() },
                { principal ->
                    sessionManager.startSession(principal, request, response)
                    ResponseEntity.ok(principal.toSessionResponse())
                },
            )

    @GetMapping("/actual")
    fun current(): SessionResponse = queryCurrentSession.execute().toSessionResponse()

    @PostMapping("/cierre")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        sessionManager.endSession(request, response)
        return ResponseEntity.noContent().build()
    }
}

private fun Principal.toSessionResponse(): SessionResponse =
    SessionResponse(
        userId = userId,
        clubId = clubId,
        role = role.code,
    )
