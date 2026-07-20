package com.runcriticon.identidad.application.ports.inbound
import com.runcriticon.identidad.application.usecases.magiclink.RequestMagicLinkCommand
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.shared.observability.OpenTelemetryHelper
import java.time.Instant
import java.util.UUID

/**
 * Evento de aplicación que solicita el envío del email de magic link. Lo publica [RequestMagicLinkCommand] dentro de su
 * transacción; el outbox de Spring Modulith lo entrega a `MagicLinkEmailListener` tras el commit, desacoplando el envío
 * de la transacción de negocio.
 *
 * [clubId] viaja siempre (flujo anónimo, sin [actorId]); ambos y [traceparent] son nullable con default para
 * deserializar filas ya en el outbox antes de este cambio. El listener los usa para restaurar el MDC
 * ([com.runcriticon.shared.observability.MdcRestorerForEvents]). [clubId] va como `UUID` crudo a propósito: este DTO se
 * serializa a JSON en el outbox y el formato de las filas persistidas debe permanecer estable.
 *
 * @property to email del destinatario.
 * @property recipientName nombre para personalizar el saludo.
 * @property rawToken token de login en claro; solo viaja en el email, nunca se persiste.
 * @property expiresAt instante de caducidad del enlace.
 */
data class MagicLinkEmailRequested(
    val to: Email,
    val recipientName: String,
    val rawToken: RawToken,
    val expiresAt: Instant,
    val clubId: UUID? = null,
    val actorId: UUID? = null,
    val traceparent: String? = OpenTelemetryHelper.actualTraceparent(),
)
