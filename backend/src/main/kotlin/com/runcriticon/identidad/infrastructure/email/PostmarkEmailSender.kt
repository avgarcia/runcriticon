package com.runcriticon.identidad.infrastructure.email

import com.runcriticon.identidad.application.ports.EmailSender
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.MagicLinkEmailRequested
import com.runcriticon.identidad.application.ports.PasswordResetEmailRequested
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Adaptador de email sobre la API HTTP de Postmark, activo en todos los perfiles salvo `local`
 * (ADR-0005). Envía el email vía `RestClient` autenticando con el server token; registra el
 * resultado en [IdentidadEmailMetrics] y propaga la excepción para que el outbox reintente.
 */
@Component
@Profile("!local")
class PostmarkEmailSender(
    private val config: EmailConfig,
    private val renderer: InvitationEmailRenderer,
    private val magicLinkRenderer: MagicLinkEmailRenderer,
    private val passwordResetRenderer: PasswordResetEmailRenderer,
    private val metrics: IdentidadEmailMetrics,
) : EmailSender {
    private val client: RestClient by lazy {
        RestClient
            .builder()
            .baseUrl(config.postmark.serverUrl)
            .defaultHeader("X-Postmark-Server-Token", config.postmark.apiKey)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build()
    }

    /** Construye el payload de Postmark y lo envía; contabiliza éxito o error en las métricas. */
    override fun sendInvitation(request: InvitationEmailRequested) {
        runCatching {
            val body =
                mapOf(
                    "From" to "${config.fromName} <${config.fromAddress}>",
                    "To" to request.to.value,
                    "Subject" to "Tu invitación a Runcriticon",
                    "HtmlBody" to
                        renderer.render(
                            request.recipientName,
                            "${config.baseUrl}/activar?token=${request.rawToken.value}",
                            request.expiresAt,
                        ),
                )
            client
                .post()
                .uri("/email")
                .body(body)
                .retrieve()
                .toBodilessEntity()
            metrics.invitationSent(success = true)
        }.onFailure { e ->
            metrics.invitationSent(success = false)
            throw e
        }
    }

    /** Construye el payload de Postmark del magic link y lo envía; contabiliza éxito o error. */
    override fun sendMagicLink(request: MagicLinkEmailRequested) {
        runCatching {
            val body =
                mapOf(
                    "From" to "${config.fromName} <${config.fromAddress}>",
                    "To" to request.to.value,
                    "Subject" to "Tu acceso a Runcriticon",
                    "HtmlBody" to
                        magicLinkRenderer.render(
                            request.recipientName,
                            "${config.baseUrl}/entrar?token=${request.rawToken.value}",
                            request.expiresAt,
                        ),
                )
            client
                .post()
                .uri("/email")
                .body(body)
                .retrieve()
                .toBodilessEntity()
            metrics.magicLinkSent(success = true)
        }.onFailure { e ->
            metrics.magicLinkSent(success = false)
            throw e
        }
    }

    /** Construye el payload de Postmark del reseteo de contraseña y lo envía; contabiliza éxito o error. */
    override fun sendPasswordReset(request: PasswordResetEmailRequested) {
        runCatching {
            val body =
                mapOf(
                    "From" to "${config.fromName} <${config.fromAddress}>",
                    "To" to request.to.value,
                    "Subject" to "Restablece tu contraseña de Runcriticon",
                    "HtmlBody" to
                        passwordResetRenderer.render(
                            request.recipientName,
                            "${config.baseUrl}/restablecer/nueva?token=${request.rawToken.value}",
                            request.expiresAt,
                        ),
                )
            client
                .post()
                .uri("/email")
                .body(body)
                .retrieve()
                .toBodilessEntity()
            metrics.passwordResetSent(success = true)
        }.onFailure { e ->
            metrics.passwordResetSent(success = false)
            throw e
        }
    }
}
