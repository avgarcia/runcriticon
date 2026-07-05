package com.runcriticon.shared.observability

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.slf4j.MDC
import java.util.UUID

class MdcRestorerForEventsTest :
    FunSpec({
        val userIdHasher = mockk<UserIdHasher>()
        val restorer = MdcRestorerForEvents(userIdHasher)

        afterEach { MDC.clear() }

        test("rellena trace_id, club_id, user_id_hash y module a partir de un traceparent valido") {
            val actorId = UUID.randomUUID()
            val clubId = UUID.randomUUID()
            every { userIdHasher.hash(actorId) } returns "hash-del-actor"

            restorer.restore(
                module = "identidad",
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                clubId = clubId,
                actorId = actorId,
            )

            MDC.get("trace_id") shouldBe "0af7651916cd43dd8448eb211c80319c"
            MDC.get("club_id") shouldBe clubId.toString()
            MDC.get("user_id_hash") shouldBe "hash-del-actor"
            MDC.get("module") shouldBe "identidad"
        }

        test("traceparent malformado: no rellena trace_id pero no lanza") {
            restorer.restore(module = "identidad", traceparent = "no-es-un-traceparent", clubId = null, actorId = null)

            MDC.get("trace_id").shouldBeNull()
            MDC.get("user_id_hash") shouldBe "system"
        }

        test("actorId null: user_id_hash es system") {
            restorer.restore(module = "identidad", traceparent = null, clubId = null, actorId = null)

            MDC.get("user_id_hash") shouldBe "system"
        }

        test("clear limpia el MDC") {
            restorer.restore(module = "identidad", traceparent = null, clubId = null, actorId = null)
            restorer.clear()

            MDC.get("module").shouldBeNull()
            MDC.get("user_id_hash").shouldBeNull()
        }

        test("restore(IntegrationEvent) deriva el modulo del paquete de la clase del evento") {
            val actorId = UUID.randomUUID()
            every { userIdHasher.hash(actorId) } returns "hash"
            val event =
                FakeIntegrationEvent(
                    eventId = UUID.randomUUID(),
                    aggregateId = UUID.randomUUID(),
                    occurredAt = java.time.Instant.now(),
                    version = 1,
                    clubId = UUID.randomUUID(),
                    actorId = actorId,
                    traceparent = null,
                )

            restorer.restore(event)

            MDC.get("module") shouldBe "shared"
        }
    })

private data class FakeIntegrationEvent(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: java.time.Instant,
    override val version: Int,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
) : com.runcriticon.shared.events.IntegrationEvent
