package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.api.events.EntrenadorAsignadoAGrupo
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class AssignCoachToGroupCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val grupo = Group.create(club, "Maratón Valencia avanzado").shouldBeRight()
        val entrenador = PersonId.of(UuidCreator.getTimeOrderedEpoch())
        val silentPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

        fun groups() =
            InMemoryGroupRepository(
                existing = mapOf(grupo.id to GroupDetail(grupo, members = emptyList(), exclusions = emptyList())),
            )

        test("asigna al entrenador, devuelve la lista recalculada y publica EntrenadorAsignadoAGrupo") {
            val repository = groups()
            val eventSlot = slot<Any>()
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

            val result =
                AssignCoachToGroupCommand(repository, InMemoryCoachLookup(setOf(entrenador)), eventPublisher)
                    .execute(admin, grupo.id.value, entrenador.value)

            result.shouldBeRight().map { it.id } shouldBe listOf(entrenador)
            repository.assignCoachCalls.single() shouldBe Triple(club, grupo.id, entrenador)

            val evento = eventSlot.captured.shouldBeInstanceOf<EntrenadorAsignadoAGrupo>()
            evento.aggregateId shouldBe entrenador.value
            evento.groupId shouldBe grupo.id.value
            evento.clubId shouldBe club.value
            evento.actorId shouldBe admin.userId
        }

        test("repetir la asignacion es idempotente") {
            val repository = groups()
            val command = AssignCoachToGroupCommand(repository, InMemoryCoachLookup(setOf(entrenador)), silentPublisher)

            command.execute(admin, grupo.id.value, entrenador.value)
            val second = command.execute(admin, grupo.id.value, entrenador.value)

            second.shouldBeRight().map { it.id } shouldBe listOf(entrenador)
        }

        test("un grupo que no existe da GroupNotFound y no toca al entrenador") {
            val repository = InMemoryGroupRepository()
            val lookup = InMemoryCoachLookup(setOf(entrenador))

            AssignCoachToGroupCommand(repository, lookup, silentPublisher)
                .execute(admin, UuidCreator.getTimeOrderedEpoch(), entrenador.value)
                .shouldBeLeft(ClubTaxonomiaError.GroupNotFound)

            lookup.calls.size shouldBe 0
            repository.assignCoachCalls.size shouldBe 0
        }

        test("un id que no es entrenador da CoachNotFound y no escribe la asignacion") {
            val repository = groups()
            val alguienQueNoEsEntrenador = PersonId.of(UuidCreator.getTimeOrderedEpoch())

            AssignCoachToGroupCommand(repository, InMemoryCoachLookup(emptySet()), silentPublisher)
                .execute(admin, grupo.id.value, alguienQueNoEsEntrenador.value)
                .shouldBeLeft(ClubTaxonomiaError.CoachNotFound)

            repository.assignCoachCalls.size shouldBe 0
        }
    })
