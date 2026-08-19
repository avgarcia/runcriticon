package com.runcriticon.auditoria.application.usecases.events

import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventFilter
import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventRepository
import com.runcriticon.auditoria.domain.AuditEvent
import com.runcriticon.shared.tenancy.ClubId
import java.util.UUID

/** Doble en memoria de [AuditEventRepository]. `search` ignora [AuditEventFilter]: la mecánica de filtros la
 * cubre el test de Testcontainers contra la query real; este doble solo sostiene el test de autorización. */
class InMemoryAuditEventRepository(
    private val events: List<AuditEvent> = emptyList(),
) : AuditEventRepository {
    val searches = mutableListOf<Pair<ClubId, AuditEventFilter>>()
    val saved = mutableListOf<AuditEvent>()
    val anonymized = mutableListOf<UUID>()

    override fun save(event: AuditEvent) {
        saved += event
    }

    override fun search(
        clubId: ClubId,
        filter: AuditEventFilter,
    ): List<AuditEvent> {
        searches += clubId to filter
        return events.filter { it.clubId == clubId }
    }

    override fun anonymize(personId: UUID): Int {
        anonymized += personId
        return 0
    }
}
