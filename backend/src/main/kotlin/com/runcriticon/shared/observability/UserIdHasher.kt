package com.runcriticon.shared.observability

import java.util.UUID

/**
 * Convierte un `userId` en un seudónimo estable apto para logs, trazas y métricas (ADR-0012 D9,
 * ADR-0013, observabilidad-por-modulo). Nunca debe emitirse el `userId` en claro en telemetría:
 * se registra siempre su hash, de modo que se puede correlacionar sin exponer PII.
 *
 * El cuerpo (HMAC con secreto desde SSM, ADR-0013 D12) se difiere; en H0 queda el contrato.
 */
interface UserIdHasher {
    /** Seudónimo estable del usuario para telemetría. La misma entrada produce siempre el mismo hash. */
    fun hash(userId: UUID): String
}
