package com.runcriticon.identidad.infrastructure.email

import org.springframework.stereotype.Component
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renderiza el cuerpo HTML del email de invitación a partir de la plantilla externa
 * `templates/email/invitation.html` (ADR-0005 D7: plantilla en fichero, no en código). Thymeleaf
 * escapa por defecto los valores (`th:text`), evitando inyección de HTML con el nombre del destinatario.
 */
@Component
class InvitationEmailRenderer(
    private val templateEngine: ITemplateEngine,
) {
    /**
     * Procesa la plantilla con los datos de la invitación y devuelve el HTML listo para enviar.
     *
     * @param recipientName nombre del destinatario (se escapa al insertarlo).
     * @param activationUrl enlace de activación completo.
     * @param expiresAt caducidad del enlace; se formatea a fecha legible en zona Europe/Madrid.
     */
    fun render(
        recipientName: String,
        activationUrl: String,
        expiresAt: Instant,
    ): String {
        val context =
            Context(SPANISH).apply {
                setVariable("recipientName", recipientName)
                setVariable("activationUrl", activationUrl)
                setVariable("expiryDisplay", EXPIRY_FORMATTER.format(expiresAt))
            }
        return templateEngine.process("email/invitation", context)
    }

    private companion object {
        val SPANISH: Locale = Locale.of("es", "ES")
        val EXPIRY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter
                .ofPattern("d 'de' MMMM 'de' yyyy", SPANISH)
                .withZone(ZoneId.of("Europe/Madrid"))
    }
}
