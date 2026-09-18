package com.runcriticon.identidad.application

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
 * Centralizado en un único componente, igual que `ClubTaxonomiaAccessAuditor`: los 12 casos de uso de este módulo
 * cubiertos por LAL-120 solo tienen la guarda RBAC — ninguno tiene una segunda guarda de nivel de objeto que
 * devuelva `IdentidadError.Forbidden` — así que todas las llamadas comparten forma exacta (`aggregateId =
 * actor.userId`, sin `sujetoId`, `motivo = "RBAC"`).
 *
 * Importa [AccesoDenegado] de `shared.api.events`, no de `auditoria.api.events`: `auditoria` ya depende de
 * `identidad` (anonimización, LAL-106/124/126), así que la dirección contraria habría formado un ciclo que
 * `ModulithFronterasTest` rechaza — a diferencia de `club_taxonomia`/`planificacion`, que sí podían importarlo de
 * `auditoria` porque `auditoria` no depende de ellos. Ver el KDoc de `AccesoDenegado` para el detalle.
 *
 * **Deliberadamente no cubre** los rechazos de autenticación pura (login, magic link, activación, reseteo de
 * contraseña): esos no pasan por `IdentidadError.Forbidden` ni por la matriz de autorización — pertenecen a la
 * auditoría de intentos fallidos de ADR-0003 D15, ya cubierta por `AuditTrail`/`AuthRateLimit`, no a esta.
 */
@Component
class IdentidadAccessAuditor(
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
