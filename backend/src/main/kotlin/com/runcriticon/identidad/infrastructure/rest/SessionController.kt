package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.AutenticarUsuario
import com.runcriticon.identidad.application.usecases.ConsultarSesionActual
import com.runcriticon.shared.autorizacion.anotaciones.NoAuthRequired
import com.runcriticon.shared.autorizacion.modelo.Principal
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
 * Endpoints de sesión (ADR-0003 D5, D10, D11). En MVP mono-club el `clubId` es fijo (config); al
 * pasar a multi-club se inferirá del subdominio (ADR-0006 D16). El handler NO toca el contexto de
 * seguridad: delega en [SecuritySessionManager] (núcleo). El error de login se trata como
 * 401 neutro (sin distinguir email inexistente de contraseña incorrecta, ADR-0003 D5).
 */
@RestController
@RequestMapping("/api/sesion")
class SessionController(
    private val autenticarUsuario: AutenticarUsuario,
    private val consultarSesionActual: ConsultarSesionActual,
    private val sessionManager: SecuritySessionManager,
    @Value("\${runcriticon.bootstrap.club-id:00000000-0000-0000-0000-000000000001}")
    private val clubId: String,
) {
    @PostMapping
    @NoAuthRequired("Login público: punto de entrada de autenticación (ADR-0003 D5)")
    fun login(
        @RequestBody credenciales: CredencialesRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<SesionResponse> {
        val principal =
            autenticarUsuario
                .ejecutar(UUID.fromString(clubId), credenciales.email, credenciales.password)
                .getOrNull()
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        sessionManager.startSession(principal, request, response)
        return ResponseEntity.ok(principal.toResponse())
    }

    @GetMapping("/actual")
    fun current(): SesionResponse = consultarSesionActual.ejecutar().toResponse()

    @PostMapping("/cierre")
    fun logout(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        sessionManager.endSession(request, response)
        return ResponseEntity.noContent().build()
    }
}

private fun Principal.toResponse(): SesionResponse =
    SesionResponse(
        userId = userId.toString(),
        clubId = clubId.toString(),
        rol = rol.codigo,
    )
