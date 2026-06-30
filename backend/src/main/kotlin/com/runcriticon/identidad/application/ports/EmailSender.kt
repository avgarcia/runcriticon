package com.runcriticon.identidad.application.ports

/**
 * Puerto de salida para el envío de emails transaccionales (ADR-0005 D2).
 * Las implementaciones se invocan desde [InvitationEmailListener] tras el commit de la transacción,
 * nunca directamente dentro de ella. El outbox de Spring Modulith garantiza la entrega y los reintentos.
 */
interface EmailSender {
    /**
     * Envía el email de invitación al destinatario indicado en [request].
     * Idempotente desde la perspectiva del outbox: puede reintentarse si la entrega falla.
     */
    fun sendInvitation(request: InvitationEmailRequested)

    /**
     * Envía el email de magic link al destinatario indicado en [request] (ADR-0003 D5).
     * Idempotente desde la perspectiva del outbox: puede reintentarse si la entrega falla.
     */
    fun sendMagicLink(request: MagicLinkEmailRequested)
}
