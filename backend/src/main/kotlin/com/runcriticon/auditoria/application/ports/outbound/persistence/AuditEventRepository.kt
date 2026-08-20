package com.runcriticon.auditoria.application.ports.outbound.persistence

import com.runcriticon.auditoria.domain.AuditEvent
import com.runcriticon.auditoria.domain.AuditEventType
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant
import java.util.UUID

interface AuditEventRepository {
    /** Persiste [event]. Único escritor: `AuditEventListener`, dentro de la transacción del outbox. */
    fun save(event: AuditEvent)

    /** Los eventos de [clubId] que cumplen [filter], del más reciente al más antiguo. */
    fun search(
        clubId: ClubId,
        filter: AuditEventFilter,
    ): List<AuditEvent>

    /**
     * Anonimiza (`actor_id`/`sujeto_id` → `NULL`) las filas donde [personId] aparece como actor o sujeto —
     * derecho al olvido (D17: anonimización, no borrado físico, a diferencia del resto de módulos). Devuelve
     * cuántas filas tocó, para el log del listener.
     */
    fun anonymize(personId: UUID): Int
}

/**
 * Filtro de la consulta forense (`GET /api/auditoria/eventos`). Sin paginación todavía — no hay ningún precedente
 * de endpoint paginado en el repo; el repositorio acota con un `LIMIT` fijo ([AuditEventRepository.search]),
 * documentado como simplificación consciente hasta que el volumen real lo exija.
 */
data class AuditEventFilter(
    val actorId: UUID? = null,
    val sujetoId: UUID? = null,
    val type: AuditEventType? = null,
    val desde: Instant? = null,
    val hasta: Instant? = null,
)
