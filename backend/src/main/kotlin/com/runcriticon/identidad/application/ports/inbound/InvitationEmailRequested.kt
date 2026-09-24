package com.runcriticon.identidad.application.ports.inbound

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.shared.observability.OpenTelemetryHelper
import java.time.Instant
import java.util.UUID

/**
 * Evento de aplicación que solicita el envío del email de invitación. Lo publica el caso de uso`InvitarEntrenador`
 * dentro de su transacción; el outbox de Spring Modulith lo entrega a [InvitationEmailListener] tras el commit,
 * desacoplando el envío de la transacción de negocio.
 *
 * [clubId], [actorId] y [traceparent] son nullable con default: filas ya en el outbox antes de este cambio deserializan
 * sin ellos. El listener los usa para restaurar el MDC ([com.runcriticon.shared.observability.MdcRestorerForEvents]).
 * [clubId] va como `UUID` crudo a propósito: este DTO se serializa a JSON en el outbox y el formato de las filas
 * persistidas debe permanecer estable. [eventId] con default (`UuidCreator.getTimeOrderedEpoch()`, LAL-144): se
 * fija una vez al publicar y viaja igual en cada reentrega del outbox, lo que permite a
 * [IdentidadProcessedEventTracker] detectar reintentos y no reenviar el email.
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
    val eventId: UUID = UuidCreator.getTimeOrderedEpoch(),
)
