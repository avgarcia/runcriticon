package com.runcriticon.identidad.infrastructure.email

import org.springframework.stereotype.Component
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renderiza el cuerpo HTML del email de magic link a partir de la plantilla externa
 * `templates/email/magic-link.html` (ADR-0005 D7: plantilla en fichero, no en código). Thymeleaf
 * escapa por defecto los valores (`th:text`), evitando inyección de HTML con el nombre del destinatario.
 */
@Component
class MagicLinkEmailRenderer(
    private val templateEngine: ITemplateEngine,
) {
    /**
     * Procesa la plantilla con los datos del magic link y devuelve el HTML listo para enviar.
     *
     * @param recipientName nombre del destinatario (se escapa al insertarlo).
     * @param loginUrl enlace de acceso completo (con el token en claro).
     * @param expiresAt caducidad del enlace; se formatea a hora legible en zona Europe/Madrid.
     */
    fun render(
        recipientName: String,
        loginUrl: String,
        expiresAt: Instant,
    ): String {
        val context =
            Context(SPANISH).apply {
                setVariable("recipientName", recipientName)
                setVariable("loginUrl", loginUrl)
                setVariable("expiryDisplay", EXPIRY_FORMATTER.format(expiresAt))
            }
        return templateEngine.process("email/magic-link", context)
    }

    private companion object {
        val SPANISH: Locale = Locale.of("es", "ES")
        val EXPIRY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter
                .ofPattern("HH:mm 'del' d 'de' MMMM", SPANISH)
                .withZone(ZoneId.of("Europe/Madrid"))
    }
}
