package com.runcriticon.shared.observability

import io.opentelemetry.api.trace.Span

/**
 * Lee el contexto de traza distribuida (W3C Trace Context) del span OpenTelemetry en curso para
 * propagarlo en los integration events (ADR-0011 D4, observabilidad-por-modulo). El bridge
 * `micrometer-tracing-bridge-otel` expone la API de OpenTelemetry; si no hay traza activa
 * (fuera de una request, o en tests sin tracer), [actualTraceparent] devuelve `null`.
 */
object OpenTelemetryHelper {
    /**
     * Cabecera `traceparent` del span actual con formato W3C `00-<trace-id>-<span-id>-<flags>`,
     * o `null` si el contexto de traza no es válido.
     */
    fun actualTraceparent(): String? {
        val context = Span.current().spanContext
        if (!context.isValid) return null
        return "00-${context.traceId}-${context.spanId}-${context.traceFlags.asHex()}"
    }
}
