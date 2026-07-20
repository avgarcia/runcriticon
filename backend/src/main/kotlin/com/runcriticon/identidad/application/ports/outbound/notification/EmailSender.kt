package com.runcriticon.identidad.application.ports.outbound.notification

import com.runcriticon.identidad.application.ports.inbound.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.inbound.MagicLinkEmailRequested
import com.runcriticon.identidad.application.ports.inbound.PasswordResetEmailRequested

/**
 * Puerto de salida para el envío de emails transaccionales.
 * Las implementaciones se invocan desde [InvitationEmailListener] tras el commit de la transacción, nunca directamente
 * dentro de ella. El outbox de Spring Modulith garantiza la entrega y los reintentos.
 */
interface EmailSender {
    /**
     * Envía el email de invitación al destinatario indicado en [request].
     * Idempotente desde la perspectiva del outbox: puede reintentarse si la entrega falla.
     */
    fun sendInvitation(request: InvitationEmailRequested)

    /**
     * Envía el email de magic link al destinatario indicado en [request].
     * Idempotente desde la perspectiva del outbox: puede reintentarse si la entrega falla.
     */
    fun sendMagicLink(request: MagicLinkEmailRequested)

    /**
     * Envía el email de reseteo de contraseña al destinatario indicado en [request].
     * Idempotente desde la perspectiva del outbox: puede reintentarse si la entrega falla.
     */
    fun sendPasswordReset(request: PasswordResetEmailRequested)
}
