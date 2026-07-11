package com.runcriticon.clubtaxonomia

import com.runcriticon.shared.events.IntegrationEvent
import java.time.Instant
import java.util.UUID

/** Solo para [com.runcriticon.shared.observability.MdcRestorerForEventsTest] (ADR-0011 D9). */
internal data class FakeClubTaxonomiaEvent(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
) : IntegrationEvent
