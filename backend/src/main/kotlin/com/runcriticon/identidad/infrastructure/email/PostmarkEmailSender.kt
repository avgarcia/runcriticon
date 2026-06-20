package com.runcriticon.identidad.infrastructure.email

import com.runcriticon.identidad.application.ports.EmailSender
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
@Profile("!local")
class PostmarkEmailSender(
    private val config: EmailConfig,
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

    override fun sendInvitation(request: InvitationEmailRequested) {
        runCatching {
            val body =
                mapOf(
                    "From" to "${config.fromName} <${config.fromAddress}>",
                    "To" to request.to.value,
                    "Subject" to "Tu invitación a Runcriticon",
                    "HtmlBody" to
                        buildInvitationHtml(
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
}
