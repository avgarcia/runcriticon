package com.runcriticon.seguimiento.application

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.shared.api.events.AccesoDenegado
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.observability.OpenTelemetryHelper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Publica [AccesoDenegado] (ADR-0009 D15-D16) en la misma transacción que el rechazo de un caso de uso de
 * `seguimiento`, igual que `PlanificacionAccessAuditor` en su módulo. Aquí importa más que en ningún otro: es el
 * módulo que guarda los datos de salud del art. 9 (reportes, marcas, lesiones), y un intento de leerlos o escribirlos
 * sin permiso tiene que quedar en `auditoria.evento`.
 *
 * Todos los rechazos de este módulo son de RBAC (la matriz): las comprobaciones de objeto son por construcción —cada
 * caso de uso lee o escribe solo los datos del propio `actor.userId`—, así que no hay objeto ajeno que nombrar en
 * `sujetoId`.
 */
@Component
class SeguimientoAccessAuditor(
    private val eventPublisher: ApplicationEventPublisher,
) {
    /** Rechazo de la matriz (RBAC), el único que hay hoy en este módulo: el agregado del evento es el propio actor. */
    fun rbacDenied(
        actor: Principal,
        resource: Resource,
        action: Action,
    ) = denegado(actor, resource, action, aggregateId = actor.userId, motivo = "RBAC")

    fun denegado(
        actor: Principal,
        resource: Resource,
        action: Action,
        aggregateId: UUID,
        motivo: String,
        sujetoId: UUID? = null,
    ) {
        eventPublisher.publishEvent(
            AccesoDenegado(
                eventId = UuidCreator.getTimeOrderedEpoch(),
                aggregateId = aggregateId,
                occurredAt = Instant.now(),
                clubId = actor.clubId,
                actorId = actor.userId,
                traceparent = OpenTelemetryHelper.actualTraceparent(),
                recurso = "${resource.name}:${action.name}",
                motivo = motivo,
                sujetoId = sujetoId,
            ),
        )
    }
}
