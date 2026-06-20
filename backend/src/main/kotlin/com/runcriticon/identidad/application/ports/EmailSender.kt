package com.runcriticon.identidad.application.ports

/**
 * Puerto de salida para el envío de emails transaccionales (ADR-0005 D2).
 * Las implementaciones se invocan desde [InvitationEmailListener] tras el commit de la transacción,
 * nunca directamente dentro de ella. El outbox de Spring Modulith garantiza la entrega y los reintentos.
 */
interface EmailSender {
    fun sendInvitation(request: InvitationEmailRequested)
}
