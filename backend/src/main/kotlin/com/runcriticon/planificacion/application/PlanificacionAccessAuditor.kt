package com.runcriticon.planificacion.application

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
 * Publica [AccesoDenegado] (ADR-0009 D15-D16) en la misma transacción que el rechazo, para los casos de uso de
 * `planificacion` que no tenían ya su propio `denegado(...)` privado (LAL-120): `AddSessionCommand`,
 * `UpdateSessionCommand`, `DeleteSessionCommand`, `CreateDraftPlanCommand`, `GetPlanQuery`, `ListDraftPlansQuery`.
 *
 * `PublishPlanCommand`, `SetPersonalizationCommand` y `RemovePersonalizationCommand` (LAL-93/LAL-26) conservan su
 * propio método privado — se dejan tal cual para no ampliar el diff de un PR que solo añade auditoría a los
 * casos de uso que aún no la tenían.
 */
@Component
class PlanificacionAccessAuditor(
    private val eventPublisher: ApplicationEventPublisher,
) {
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
