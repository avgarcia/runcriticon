package com.runcriticon.identidad.application.ports.inbound

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.observability.OpenTelemetryHelper
import java.util.UUID

/**
 * Evento de aplicación interno de `identidad`: la cuenta [userId] lleva en baja más de la gracia y hay que suprimirla
 * (ADR-0017 D9). Lo publica el job diario de las bajas vencidas, una cuenta por transacción; el outbox de Spring
 * Modulith lo entrega a `DeletionGraceExpiredListener` tras el commit.
 *
 * Existe porque el job no tiene principal y los repositorios de la supresión exigen uno de su club
 * (`@AuthScope(Scope.CLUB)`): el listener lo obtiene de este evento con `ListenerPrincipalScope`, que solo pueden usar
 * los listeners.
 *
 * No es un integration event: no sale de `identidad`. [clubId] y [userId] van como `UUID` crudo porque este DTO se
 * serializa a JSON en el outbox. [eventId] se fija al publicar y viaja igual en cada reentrega, lo que permite al
 * listener descartar los reintentos.
 *
 * @property role rol de la cuenta, para construir el principal con el que el listener la suprime.
 */
data class DeletionGraceExpired(
    val userId: UUID,
    val clubId: UUID,
    val role: Role,
    val traceparent: String? = OpenTelemetryHelper.actualTraceparent(),
    val eventId: UUID = UuidCreator.getTimeOrderedEpoch(),
)
