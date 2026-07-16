package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.ratelimit.ProgressiveThrottle
import com.runcriticon.identidad.application.ratelimit.RateLimitMetrics
import com.runcriticon.identidad.application.ratelimit.ThrottleProfile
import com.runcriticon.identidad.application.usecases.AuthenticateUser
import com.runcriticon.identidad.application.usecases.ChangeExpiredPassword
import com.runcriticon.identidad.application.usecases.LoginOutcome
import com.runcriticon.identidad.application.usecases.QueryCurrentSession
import com.runcriticon.identidad.infrastructure.ratelimit.ClientIpResolver
import com.runcriticon.shared.autorizacion.annotations.AuthenticatedOnly
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.ClubId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.spring.SecuritySessionManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

/**
 * Endpoints de sesión (ADR-0003 D5, D7, D10, D11). En MVP mono-club el `clubId` es fijo (config); al
 * pasar a multi-club se inferirá del subdominio (ADR-0006 D16). El handler NO toca el contexto de
 * seguridad: delega en [SecuritySessionManager] (núcleo). El login fallido es 401 neutro (sin
 * distinguir email inexistente de contraseña incorrecta, ADR-0003 D5); la contraseña caducada es 409
 * con `code=PASSWORD_EXPIRED` y NO crea sesión: el cliente lleva al cambio forzado (ADR-0003 D7).
 *
 * **Throttling progresivo de login (ADR-0003 D12, LAL-35)**: tras fallos consecutivos se responde
 * `429` con `Retry-After` creciente (1s, 5s, 15s, 60s…) por IP y por cuenta, en lugar de bloquear la
 * cuenta (que permitiría a un atacante causar denegación de servicio a la víctima). Un login correcto
 * o una contraseña caducada válida reinician el backoff.
 */
@RestController
@RequestMapping("/api/sesion")
class SessionController(
    private val authenticateUser: AuthenticateUser,
    private val changeExpiredPassword: ChangeExpiredPassword,
    private val queryCurrentSession: QueryCurrentSession,
    private val sessionManager: SecuritySessionManager,
    private val throttle: ProgressiveThrottle,
    private val clientIpResolver: ClientIpResolver,
    private val rateLimitMetrics: RateLimitMetrics,
    @Value("\${runcriticon.bootstrap.club-id:00000000-0000-0000-0000-000000000001}")
    private val clubId: String,
) {
    @PostMapping
    @NoAuthRequired("Login público: punto de entrada de autenticación (ADR-0003 D5)")
    fun login(
        @RequestBody credenciales: CredentialsRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> {
        val ipKey = "ip:" + clientIpResolver.resolve(request)
        val accountKey = "acct:" + credenciales.email.trim().lowercase()

        val throttled = loginThrottleWait(ipKey, accountKey)
        if (throttled != null) return throttled

        return authenticateUser
            .execute(ClubId.of(UUID.fromString(clubId)), credenciales.email, credenciales.password)
            .fold(
                {
                    // Fallo de credenciales: sube el backoff por IP y por cuenta (no bloquea la cuenta).
                    throttle.penalize(ThrottleProfile.LOGIN, ipKey)
                    throttle.penalize(ThrottleProfile.LOGIN, accountKey)
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED).build<Any>()
                },
                { outcome ->
                    // Contraseña correcta (aunque esté caducada): reinicia el backoff.
                    throttle.reset(ThrottleProfile.LOGIN, ipKey)
                    throttle.reset(ThrottleProfile.LOGIN, accountKey)
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
    }

    /** Devuelve la respuesta 429 si la IP o la cuenta están en backoff; `null` si el login puede seguir. */
    private fun loginThrottleWait(
        ipKey: String,
        accountKey: String,
    ): ResponseEntity<ErrorResponse>? {
        val ipWait = throttle.check(ThrottleProfile.LOGIN, ipKey)
        val accountWait = throttle.check(ThrottleProfile.LOGIN, accountKey)
        val wait = listOfNotNull(ipWait, accountWait).maxOrNull() ?: return null
        rateLimitMetrics.blocked("login", if (wait == ipWait) "ip" else "cuenta")
        return tooManyRequests(wait)
    }

    private fun tooManyRequests(wait: Duration): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, wait.toSeconds().coerceAtLeast(1).toString())
            .body(
                ErrorResponse(
                    code = "RATE_LIMITED",
                    field = null,
                    message = "Demasiados intentos; inténtalo de nuevo más tarde.",
                ),
            )

    @PostMapping("/contrasena")
    @NoAuthRequired("Cambio de contraseña caducada: revalida con la contraseña actual (ADR-0003 D7)")
    fun changePassword(
        @RequestBody req: PasswordChangeRequest,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<*> =
        changeExpiredPassword
            .execute(ClubId.of(UUID.fromString(clubId)), req.email, req.currentPassword, req.newPassword)
            .fold(
                { error -> error.toErrorResponse() },
                { principal ->
                    sessionManager.startSession(principal, request, response)
                    ResponseEntity.ok(principal.toSessionResponse())
                },
            )

    @GetMapping("/actual")
    @AuthenticatedOnly("Devuelve el propio principal de la sesión; no hay recurso que autorizar (LAL-37)")
    fun current(): SessionResponse = queryCurrentSession.execute().toSessionResponse()

    @PostMapping("/cierre")
    @AuthenticatedOnly("Cierra la propia sesión del llamador; no hay recurso de terceros que autorizar")
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
        role = SessionResponse.Role.forValue(role.code),
    )
