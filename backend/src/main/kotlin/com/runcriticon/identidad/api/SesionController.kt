package com.runcriticon.identidad.api

import com.runcriticon.identidad.application.AutenticarUsuario
import com.runcriticon.identidad.application.ConsultarSesionActual
import com.runcriticon.shared.autorizacion.GestorDeSesionDeSeguridad
import com.runcriticon.shared.autorizacion.NoAuthRequired
import com.runcriticon.shared.autorizacion.Principal
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
 * seguridad: delega en [GestorDeSesionDeSeguridad] (núcleo). El error de login se trata como
 * 401 neutro (sin distinguir email inexistente de contraseña incorrecta, ADR-0003 D5).
 */
@RestController
@RequestMapping("/api/sesion")
class SesionController(
    private val autenticarUsuario: AutenticarUsuario,
    private val consultarSesionActual: ConsultarSesionActual,
    private val gestorDeSesion: GestorDeSesionDeSeguridad,
    @Value("\${runcriticon.bootstrap.club-id:00000000-0000-0000-0000-000000000001}")
    private val clubId: String,
) {
    @PostMapping
    @NoAuthRequired("Login público: punto de entrada de autenticación (ADR-0003 D5)")
    fun iniciar(
        @RequestBody credenciales: CredencialesRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<SesionResponse> {
        val principal =
            autenticarUsuario
                .ejecutar(UUID.fromString(clubId), credenciales.email, credenciales.password)
                .getOrNull()
                ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

        gestorDeSesion.iniciarSesion(principal, request, response)
        return ResponseEntity.ok(principal.aResponse())
    }

    @GetMapping("/actual")
    fun actual(): SesionResponse = consultarSesionActual.ejecutar().aResponse()

    @PostMapping("/cierre")
    fun cerrar(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        gestorDeSesion.cerrarSesion(request, response)
        return ResponseEntity.noContent().build()
    }
}

private fun Principal.aResponse(): SesionResponse =
    SesionResponse(
        userId = userId.toString(),
        clubId = clubId.toString(),
        rol = rol.codigo,
    )
