package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.shared.observability.OpenTelemetryHelper
import java.time.Instant
import java.util.UUID

/**
 * Evento de aplicación que solicita el envío del email de invitación. Lo publica el caso de uso
 * `InvitarEntrenador` (LAL-46) dentro de su transacción; el outbox de Spring Modulith lo entrega a
 * [InvitationEmailListener] tras el commit, desacoplando el envío de la transacción de negocio.
 *
 * [clubId], [actorId] y [traceparent] son nullable con default: filas ya en el outbox antes de este
 * cambio deserializan sin ellos (LAL-59). El listener los usa para restaurar el MDC
 * ([com.runcriticon.shared.observability.MdcRestorerForEvents]). [clubId] va como `UUID` crudo a
 * propósito (excepción al typed ID de ADR-0008, LAL-61): este DTO se serializa a JSON en el outbox
 * y el formato de las filas persistidas debe permanecer estable.
 *
 * @property to email del destinatario.
 * @property recipientName nombre para personalizar el saludo.
 * @property rawToken token de activación en claro; solo viaja en el email, nunca se persiste.
 * @property expiresAt instante de caducidad del enlace de activación.
 */
data class InvitationEmailRequested(
    val to: Email,
    val recipientName: String,
    val rawToken: RawToken,
    val expiresAt: Instant,
    val clubId: UUID? = null,
    val actorId: UUID? = null,
    val traceparent: String? = OpenTelemetryHelper.actualTraceparent(),
)
