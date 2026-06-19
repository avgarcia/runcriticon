package com.runcriticon.identidad.infrastructure.email

import com.runcriticon.identidad.application.ports.EmailSender
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Instant

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

    override fun sendInvitation(
        to: Email,
        recipientName: String,
        rawToken: RawToken,
        expiresAt: Instant,
    ) {
        runCatching {
            val body =
                mapOf(
                    "From" to "${config.fromName} <${config.fromAddress}>",
                    "To" to to.value,
                    "Subject" to "Tu invitación a Runcriticon",
                    "HtmlBody" to
                        buildInvitationHtml(
                            recipientName,
                            "${config.baseUrl}/activar?token=${rawToken.value}",
                            expiresAt,
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
