package com.runcriticon.shared.observability

import java.util.UUID

/**
 * Convierte un `userId` en un seudónimo estable apto para logs, trazas y métricas. Nunca debe emitirse el `userId` en
 * claro en telemetría: se registra siempre su hash, de modo que se puede correlacionar sin exponer PII.
 */
interface UserIdHasher {
    /** Seudónimo estable del usuario para telemetría. La misma entrada produce siempre el mismo hash. */
    fun hash(userId: UUID): String
}
