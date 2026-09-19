package com.runcriticon.identidad.application.ratelimit

import arrow.core.raise.Raise
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import java.time.Instant
import java.util.UUID

/**
 * Aplica el límite por actor a un flujo de invitación/reenvío (100/h). Consume un token del bucket
 * [RateLimitScope.INVITATION_ACTOR] del actor; si está agotado, registra métrica + asiento `INVITACION_RATE_LIMITED` y
 * corta con [IdentidadError.RateLimited] (→ 429 con `Retry-After`).
 * Extensión de `Raise` para compartir la lógica entre los cuatro casos de uso sin duplicarla.
 */
fun Raise<IdentidadError>.consumeForActor(
    rateLimiter: RateLimiter,
    metrics: RateLimitMetrics,
    auditTrail: AuditTrail,
    actorId: UUID,
) {
    val decision = rateLimiter.tryConsume(RateLimitScope.INVITATION_ACTOR, actorId.toString())
    if (decision is RateLimitDecision.Limited) {
        metrics.blocked("invitacion", "actor")
        auditTrail.record(
            AuditEntry(
                type = AuditEventType.INVITACION_RATE_LIMITED,
                actorId = actorId,
                subjectId = null,
                occurredAt = Instant.now(),
            ),
        )
        raise(IdentidadError.RateLimited(decision.retryAfter.toSeconds().coerceAtLeast(1)))
    }
}

/**
 * Aplica el límite por IP a un flujo anónimo sin dimensión "cuenta" (LAL-64: resolver invitación por token — el
 * token ya es el secreto, no hay email que enumerar detrás). Simétrico a [consumeForActor], pero sin asiento de
 * auditoría propio: quien llama decide si registrar algo, igual que el resto de flujos por IP del módulo.
 */
fun Raise<IdentidadError>.consumeForIp(
    rateLimiter: RateLimiter,
    metrics: RateLimitMetrics,
    scope: RateLimitScope,
    metricLabel: String,
    clientIp: String,
) {
    val decision = rateLimiter.tryConsume(scope, clientIp)
    if (decision is RateLimitDecision.Limited) {
        metrics.blocked(metricLabel, "ip")
        raise(IdentidadError.RateLimited(decision.retryAfter.toSeconds().coerceAtLeast(1)))
    }
}
