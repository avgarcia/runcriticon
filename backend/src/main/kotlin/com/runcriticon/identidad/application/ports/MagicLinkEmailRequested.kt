package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.shared.observability.OpenTelemetryHelper
import java.time.Instant
import java.util.UUID

/**
 * Evento de aplicación que solicita el envío del email de magic link. Lo publica [RequestMagicLink]
 * dentro de su transacción; el outbox de Spring Modulith lo entrega a `MagicLinkEmailListener` tras el
 * commit, desacoplando el envío de la transacción de negocio.
 *
 * [clubId] viaja siempre (flujo anónimo, sin [actorId]); ambos y [traceparent] son nullable con
 * default para deserializar filas ya en el outbox antes de este cambio (LAL-59). El listener los usa
 * para restaurar el MDC ([com.runcriticon.shared.observability.MdcRestorerForEvents]).
 *
 * @property to email del destinatario.
 * @property recipientName nombre para personalizar el saludo.
 * @property rawToken token de login en claro; solo viaja en el email, nunca se persiste.
 * @property expiresAt instante de caducidad del enlace (15 min, ADR-0003 D5).
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
