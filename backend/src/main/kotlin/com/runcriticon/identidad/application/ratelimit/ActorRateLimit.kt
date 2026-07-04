package com.runcriticon.identidad.application.ratelimit

import arrow.core.raise.Raise
import com.runcriticon.identidad.application.ports.AuditTrail
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import java.time.Instant
import java.util.UUID

/**
 * Aplica el límite por actor a un flujo de invitación/reenvío (100/h, ADR-0003 D12). Consume un
 * token del bucket [RateLimitScope.INVITATION_ACTOR] del actor; si está agotado, registra métrica +
 * asiento `INVITACION_RATE_LIMITED` y corta con [IdentidadError.RateLimited] (→ 429 con `Retry-After`).
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
