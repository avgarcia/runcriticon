package com.runcriticon.planificacion.application.usecases.sessions

import com.runcriticon.planificacion.application.usecases.plans.InMemoryCoachGroupLookup
import com.runcriticon.planificacion.application.usecases.plans.InMemoryWeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.SessionVolume
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate
import java.util.UUID

class AddSessionCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val monday = LocalDate.of(2026, 8, 17)
        val plan = WeeklyPlan.createDraft(club, group, PersonId.of(coach.userId), monday).shouldBeRight()

        test("un entrenador con relacion con el grupo anade la sesion y la persiste") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))

            val session =
                AddSessionCommand(repository, lookup)
                    .execute(
                        actor = coach,
                        planId = plan.id,
                        day = monday.plusDays(1),
                        type = SessionType.RODAJE,
                        volume = SessionVolume.Distance(meters = 8000),
                        pace = null,
                        notes = null,
                    ).shouldBeRight()

            repository.findById(club, plan.id)!!.sessions shouldBe listOf(session)
        }

        test("un dia fuera de la semana del plan se rechaza sin persistir") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))

            val error =
                AddSessionCommand(repository, lookup)
                    .execute(coach, plan.id, monday.plusDays(9), SessionType.RODAJE, null, null, null)
                    .shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "dia"
            repository.findById(club, plan.id)!!.sessions shouldBe emptyList()
        }

        test("dos sesiones el mismo dia: la segunda se rechaza con DuplicateSessionDay") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val command = AddSessionCommand(repository, lookup)
            command.execute(coach, plan.id, monday.plusDays(1), SessionType.RODAJE, null, null, null).shouldBeRight()

            command
                .execute(coach, plan.id, monday.plusDays(1), SessionType.SERIES, null, null, null)
                .shouldBeLeft(PlanificacionError.DuplicateSessionDay)

            repository.findById(club, plan.id)!!.sessions.size shouldBe 1
        }

        test("un plan que no existe da Forbidden") {
            val repository = InMemoryWeeklyPlanRepository()
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))

            AddSessionCommand(repository, lookup)
                .execute(coach, PlanId.new(), monday.plusDays(1), SessionType.RODAJE, null, null, null)
                .shouldBeLeft(PlanificacionError.Forbidden)
        }

        test("un entrenador expulsado del grupo ya no puede anadir sesiones a su plan viejo") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(emptySet())

            AddSessionCommand(repository, lookup)
                .execute(coach, plan.id, monday.plusDays(1), SessionType.RODAJE, null, null, null)
                .shouldBeLeft(PlanificacionError.Forbidden)

            repository.findById(club, plan.id)!!.sessions shouldBe emptyList()
        }
    })
