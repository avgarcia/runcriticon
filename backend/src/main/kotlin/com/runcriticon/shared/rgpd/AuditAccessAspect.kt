package com.runcriticon.shared.rgpd

import arrow.core.Either
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.auditoria.api.events.AccesoADatosSensibles
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.observability.OpenTelemetryHelper
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Publica `AccesoADatosSensibles` (ADR-0009 D15) tras un caso de uso anotado con [AuditAccess], si el
 * resultado fue [Either.Right] y ese valor implementa [AuditSubjects] — un caso de uso que no expone
 * sujetos de terceros (p. ej. devuelve `Unit`, o el propio perfil del actor) no debe implementarla, y
 * entonces no se publica nada: la anotación por sí sola no basta, hace falta la interfaz.
 *
 * `args(actor,..)` liga el primer parámetro del método anotado: todo caso de uso de este monorepo tiene la
 * forma `execute(actor: Principal, ...)` (ver `AuthorizationArchTest`), así que el aspecto no necesita
 * `PrincipalProvider` — el `actor` real de la operación es el del propio método, no el del hilo.
 *
 * Un evento por sujeto en [AuditSubjects.auditSubjectIds]: el "tercero" cuyos datos se leyeron varía fila a
 * fila cuando el caso de uso devuelve una lista (p. ej. `ListCoachAlertsQuery`), a diferencia de
 * `AccesoDenegado`, que cada caso de uso publica a mano dentro de su propio `ensure` (ver
 * `PublishPlanCommand.denegado`) porque ahí sí hay un único sujeto por invocación.
 */
@Aspect
@Component
class AuditAccessAspect(
    private val eventPublisher: ApplicationEventPublisher,
) {
    @AfterReturning(pointcut = "@annotation(auditAccess) && args(actor,..)", returning = "result")
    fun publishIfSuccessful(
        auditAccess: AuditAccess,
        actor: Principal,
        result: Any?,
    ) {
        val subjectIds =
            when (result) {
                is Either.Right<*> -> (result.value as? AuditSubjects)?.auditSubjectIds()
                else -> null
            } ?: return

        subjectIds.forEach { subjectId ->
            eventPublisher.publishEvent(
                AccesoADatosSensibles(
                    eventId = UuidCreator.getTimeOrderedEpoch(),
                    aggregateId = subjectId,
                    occurredAt = Instant.now(),
                    clubId = actor.clubId,
                    actorId = actor.userId,
                    traceparent = OpenTelemetryHelper.actualTraceparent(),
                    recurso = auditAccess.resource,
                    sujetoId = subjectId,
                ),
            )
        }
    }
}
