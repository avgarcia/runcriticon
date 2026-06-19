package com.runcriticon.identidad.infrastructure.email

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun buildInvitationHtml(
    recipientName: String,
    activationUrl: String,
    expiresAt: Instant,
): String {
    val formatter =
        DateTimeFormatter
            .ofPattern("d 'de' MMMM 'de' yyyy", Locale("es", "ES"))
            .withZone(ZoneId.of("Europe/Madrid"))
    val expiryDisplay = formatter.format(expiresAt)
    return """
        <!DOCTYPE html>
        <html lang="es">
        <head><meta charset="UTF-8"><title>Invitación a Runcriticon</title></head>
        <body style="font-family:sans-serif;max-width:600px;margin:auto;padding:24px;">
          <h2>Hola, $recipientName</h2>
          <p>Has sido invitado/a a Runcriticon. Haz clic en el siguiente enlace para activar tu cuenta:</p>
          <p>
            <a href="$activationUrl"
               style="display:inline-block;padding:12px 24px;background:#1976d2;color:#fff;
                      text-decoration:none;border-radius:4px;">
              Activar mi cuenta
            </a>
          </p>
          <p>Este enlace expira el <strong>$expiryDisplay</strong>.</p>
          <p style="color:#888;font-size:12px;">Si no esperabas esta invitación, ignora este mensaje.</p>
        </body>
        </html>
        """.trimIndent()
}
