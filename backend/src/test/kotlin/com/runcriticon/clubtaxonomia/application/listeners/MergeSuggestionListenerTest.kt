package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.MergeSuggestionMetrics
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.MergeSuggestionRepository
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestion
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Comportamiento de [MergeSuggestionListener] aislado de la base de datos: qué recalcula por cada evento y qué
 * respeta un descarte ya registrado. La guarda de idempotencia comparte doble con [PersonProjectionListenerTest]
 * ([InMemoryProcessedEventTracker]): mismo contrato exacto de `evento_procesado`.
 */
class MergeSuggestionListenerTest :
    FunSpec({
        val now = Instant.parse("2026-08-25T10:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val club = ClubId.of(UUID.randomUUID())

        lateinit var groupRepository: GroupRepository
        lateinit var suggestionRepository: MergeSuggestionRepository
        lateinit var metrics: MergeSuggestionMetrics
        lateinit var listener: MergeSuggestionListener

        beforeEach {
            groupRepository = mockk(relaxed = true)
            suggestionRepository = mockk(relaxed = true)
            metrics = mockk(relaxed = true)
            listener =
                MergeSuggestionListener(
                    groupRepository = groupRepository,
                    suggestionRepository = suggestionRepository,
                    processedEvents = InMemoryProcessedEventTracker(),
                    mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher),
                    clock = clock,
                    metrics = metrics,
                )
            every { groupRepository.resolveAllMembers(club) } returns emptyMap()
        }

        fun event(
            groupId: GroupId,
            members: Set<PersonId>,
        ) = MembresiaDeGrupoCambiada(
            eventId = UUID.randomUUID(),
            aggregateId = groupId.value,
            occurredAt = now,
            clubId = club.value,
            actorId = null,
            traceparent = null,
            alumnos = members.map { it.value },
        )

        fun aPerson() = PersonId.of(UUID.randomUUID())

        test("un grupo con 2 alumnos o menos guarda una sugerencia MICRO") {
            val groupId = GroupId.new()
            val slot = slot<MergeSuggestion>()
            every { suggestionRepository.upsert(club, capture(slot)) } returns Unit

            listener.on(event(groupId, setOf(aPerson(), aPerson())))

            slot.captured.groupIdA shouldBe groupId
            slot.captured.groupIdB shouldBe groupId
            slot.captured.type shouldBe MergeSuggestionType.MICRO
            slot.captured.calculatedAt shouldBe now
        }

        test("un grupo con mas de 2 alumnos borra la sugerencia MICRO si la habia") {
            val groupId = GroupId.new()

            listener.on(event(groupId, setOf(aPerson(), aPerson(), aPerson())))

            verify { suggestionRepository.delete(club, groupId, groupId, MergeSuggestionType.MICRO) }
            verify(exactly = 0) { suggestionRepository.upsert(club, match { it.type == MergeSuggestionType.MICRO }) }
        }

        test("dos grupos que comparten el 100 porciento de sus alumnos guardan una sugerencia DUPLICADO") {
            val groupId = GroupId.new()
            val otherGroupId = GroupId.new()
            val shared = setOf(aPerson(), aPerson(), aPerson())
            every { groupRepository.resolveAllMembers(club) } returns mapOf(otherGroupId to shared)
            val slot = slot<MergeSuggestion>()
            every { suggestionRepository.upsert(club, capture(slot)) } returns Unit

            listener.on(event(groupId, shared))

            val expected = MergeSuggestion.canonicalPair(groupId, otherGroupId)
            slot.captured.type shouldBe MergeSuggestionType.DUPLICADO
            slot.captured.groupIdA shouldBe expected.first
            slot.captured.groupIdB shouldBe expected.second
        }

        test("dos grupos con menos del 80 porciento de solape borran la sugerencia DUPLICADO si la habia") {
            val groupId = GroupId.new()
            val otherGroupId = GroupId.new()
            every { groupRepository.resolveAllMembers(club) } returns
                mapOf(otherGroupId to setOf(aPerson(), aPerson(), aPerson(), aPerson()))

            listener.on(event(groupId, setOf(aPerson())))

            val (a, b) = MergeSuggestion.canonicalPair(groupId, otherGroupId)
            verify { suggestionRepository.delete(club, a, b, MergeSuggestionType.DUPLICADO) }
        }

        test("el propio grupo del evento no se compara consigo mismo") {
            val groupId = GroupId.new()
            val members = setOf(aPerson())
            every { groupRepository.resolveAllMembers(club) } returns mapOf(groupId to members)

            listener.on(event(groupId, members))

            verify(exactly = 0) {
                suggestionRepository.upsert(club, match { it.type == MergeSuggestionType.DUPLICADO })
            }
        }

        test("reentregar el mismo evento no vuelve a recalcular") {
            val groupId = GroupId.new()
            val evt = event(groupId, setOf(aPerson(), aPerson()))
            val realListener =
                MergeSuggestionListener(
                    groupRepository = groupRepository,
                    suggestionRepository = suggestionRepository,
                    processedEvents = InMemoryProcessedEventTracker(),
                    mdcRestorer = MdcRestorerForEvents(ConstantUserIdHasher),
                    clock = clock,
                    metrics = metrics,
                )

            realListener.on(evt)
            realListener.on(evt)

            verify(exactly = 1) { suggestionRepository.upsert(club, any()) }
        }
    })
