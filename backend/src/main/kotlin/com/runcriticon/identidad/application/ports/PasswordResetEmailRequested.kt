package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import java.time.Instant

/**
 * Evento de aplicación que solicita el envío del email de reseteo de contraseña (ADR-0003 D8). Lo
 * publica [com.runcriticon.identidad.application.usecases.RequestPasswordReset] dentro de su
 * transacción; el outbox de Spring Modulith lo entrega a `PasswordResetEmailListener` tras el commit,
 * desacoplando el envío de la transacción de negocio (espejo de [MagicLinkEmailRequested]).
 *
 * @property to email del destinatario.
 * @property recipientName nombre para personalizar el saludo.
 * @property rawToken token de reseteo en claro; solo viaja en el email, nunca se persiste.
 * @property expiresAt instante de caducidad del enlace (15 min, ADR-0003 D8).
 */
data class PasswordResetEmailRequested(
    val to: Email,
    val recipientName: String,
    val rawToken: RawToken,
    val expiresAt: Instant,
)
