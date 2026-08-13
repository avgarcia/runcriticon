package com.runcriticon.planificacion.application.usecases.plans

import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanStatus
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.UUID

class CreateDraftPlanCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val monday = LocalDate.of(2026, 8, 17)

        test("un entrenador con relacion con el grupo crea el plan y lo guarda") {
            val repository = InMemoryWeeklyPlanRepository()
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val command = CreateDraftPlanCommand(repository, lookup)

            val plan = command.execute(coach, group.value, monday).shouldBeRight()

            plan.status shouldBe PlanStatus.BORRADOR
            plan.coachId shouldBe PersonId.of(coach.userId)
            repository.saved.single().second shouldBe plan
        }

        test("un entrenador sin relacion con el grupo recibe Forbidden y no escribe nada") {
            val repository = InMemoryWeeklyPlanRepository()
            val lookup = InMemoryCoachGroupLookup(emptySet())
            val command = CreateDraftPlanCommand(repository, lookup)

            command
                .execute(coach, group.value, monday)
                .shouldBeLeft(PlanificacionError.Forbidden)

            repository.saved.size shouldBe 0
        }

        test("el alumno no puede crear planes y no llega a comprobar la relacion con el grupo") {
            val repository = InMemoryWeeklyPlanRepository()
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val student = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)

            CreateDraftPlanCommand(repository, lookup)
                .execute(student, group.value, monday)
                .shouldBeLeft(PlanificacionError.Forbidden)

            lookup.calls.size shouldBe 0
            repository.saved.size shouldBe 0
        }

        test("un dia que no es lunes se rechaza sin escribir nada") {
            val repository = InMemoryWeeklyPlanRepository()
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))

            CreateDraftPlanCommand(repository, lookup)
                .execute(coach, group.value, monday.plusDays(1))
                .shouldBeLeft()

            repository.saved.size shouldBe 0
        }
    })
