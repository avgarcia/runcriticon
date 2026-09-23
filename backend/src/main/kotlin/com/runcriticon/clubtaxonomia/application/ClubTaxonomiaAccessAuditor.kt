package com.runcriticon.clubtaxonomia.application

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.shared.api.events.AccesoDenegado
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.observability.OpenTelemetryHelper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant

private const val MOTIVO_RBAC = "RBAC"

/**
 * Publica [AccesoDenegado] (ADR-0009 D15-D16) cuando la matriz de autorización rechaza al [Principal] que llama —
 * en la misma transacción que el rechazo, mismo criterio que `planificacion.PublishPlanCommand.denegado(...)`
 * (LAL-93 AC3).
 *
 * Centralizado en un único componente, a diferencia de `planificacion`: los casos de uso de este módulo
 * solo tienen la guarda RBAC — ninguno tiene todavía una segunda guarda de nivel de objeto que devuelva
 * `ClubTaxonomiaError.Forbidden` — así que las 30 llamadas comparten forma exacta (`aggregateId = actor.userId`,
 * sin `sujetoId`, `motivo = "RBAC"`). El día que un caso de uso necesite un motivo distinto, este componente es
 * el sitio donde añadir el parámetro.
 */
@Component
class ClubTaxonomiaAccessAuditor(
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun denegado(
        actor: Principal,
        resource: Resource,
        action: Action,
    ) {
        eventPublisher.publishEvent(
            AccesoDenegado(
                eventId = UuidCreator.getTimeOrderedEpoch(),
                aggregateId = actor.userId,
                occurredAt = Instant.now(),
                clubId = actor.clubId,
                actorId = actor.userId,
                traceparent = OpenTelemetryHelper.actualTraceparent(),
                recurso = "${resource.name}:${action.name}",
                motivo = MOTIVO_RBAC,
            ),
        )
    }
}
