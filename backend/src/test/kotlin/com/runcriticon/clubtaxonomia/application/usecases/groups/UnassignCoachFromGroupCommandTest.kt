package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.api.events.EntrenadorEliminadoDeGrupo
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

class UnassignCoachFromGroupCommandTest :
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

        test("desvincula al entrenador ya asignado y publica EntrenadorEliminadoDeGrupo") {
            val repository = groups()
            repository.assignCoach(club, grupo.id, entrenador)
            val eventSlot = slot<Any>()
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            every { eventPublisher.publishEvent(capture(eventSlot)) } returns Unit

            UnassignCoachFromGroupCommand(repository, eventPublisher)
                .execute(admin, grupo.id.value, entrenador.value)
                .shouldBeRight()

            repository.findCoaches(club, grupo.id) shouldBe emptyList()

            val evento = eventSlot.captured.shouldBeInstanceOf<EntrenadorEliminadoDeGrupo>()
            evento.aggregateId shouldBe entrenador.value
            evento.groupId shouldBe grupo.id.value
        }

        test("desvincular a quien no estaba asignado no falla -- idempotente, y publica igualmente") {
            val repository = groups()

            UnassignCoachFromGroupCommand(repository, silentPublisher)
                .execute(admin, grupo.id.value, entrenador.value)
                .shouldBeRight()
        }

        test("no comprueba que el entrenador siga existiendo: limpia asignaciones huerfanas") {
            val repository = groups()
            repository.assignCoach(club, grupo.id, entrenador)

            // Ninguna guarda de CoachLookup en el constructor: si hiciera falta comprobar al entrenador, este test
            // no compilaría sin inyectar un InMemoryCoachLookup.
            UnassignCoachFromGroupCommand(repository, silentPublisher)
                .execute(admin, grupo.id.value, entrenador.value)
                .shouldBeRight()
        }

        test("un grupo que no existe da GroupNotFound") {
            UnassignCoachFromGroupCommand(InMemoryGroupRepository(), silentPublisher)
                .execute(admin, UuidCreator.getTimeOrderedEpoch(), entrenador.value)
                .shouldBeLeft(ClubTaxonomiaError.GroupNotFound)
        }
    })
