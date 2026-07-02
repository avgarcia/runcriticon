package com.runcriticon.identidad.infrastructure.email

import org.springframework.stereotype.Component
import org.thymeleaf.ITemplateEngine
import org.thymeleaf.context.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renderiza el cuerpo HTML del email de reseteo de contraseña a partir de la plantilla externa
 * `templates/email/password-reset.html` (ADR-0005 D7: plantilla en fichero, no en código; ADR-0003
 * D8). Thymeleaf escapa por defecto los valores (`th:text`), evitando inyección de HTML con el nombre
 * del destinatario. Espejo de [MagicLinkEmailRenderer].
 */
@Component
class PasswordResetEmailRenderer(
    private val templateEngine: ITemplateEngine,
) {
    /**
     * Procesa la plantilla con los datos del reseteo y devuelve el HTML listo para enviar.
     *
     * @param recipientName nombre del destinatario (se escapa al insertarlo).
     * @param resetUrl enlace de reseteo completo (con el token en claro).
     * @param expiresAt caducidad del enlace; se formatea a hora legible en zona Europe/Madrid.
     */
    fun render(
        recipientName: String,
        resetUrl: String,
        expiresAt: Instant,
    ): String {
        val context =
            Context(SPANISH).apply {
                setVariable("recipientName", recipientName)
                setVariable("resetUrl", resetUrl)
                setVariable("expiryDisplay", EXPIRY_FORMATTER.format(expiresAt))
            }
        return templateEngine.process("email/password-reset", context)
    }

    private companion object {
        val SPANISH: Locale = Locale.of("es", "ES")
        val EXPIRY_FORMATTER: DateTimeFormatter =
            DateTimeFormatter
                .ofPattern("HH:mm 'del' d 'de' MMMM", SPANISH)
                .withZone(ZoneId.of("Europe/Madrid"))
    }
}
