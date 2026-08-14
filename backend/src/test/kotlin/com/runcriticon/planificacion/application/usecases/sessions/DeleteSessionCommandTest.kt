package com.runcriticon.planificacion.application.usecases.sessions

import com.runcriticon.planificacion.application.usecases.plans.InMemoryCoachGroupLookup
import com.runcriticon.planificacion.application.usecases.plans.InMemoryWeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.UUID

class DeleteSessionCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val monday = LocalDate.of(2026, 8, 17)
        val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
        val plan =
            WeeklyPlan
                .createDraft(club, group, PersonId.of(coach.userId), monday)
                .shouldBeRight()
                .addSession(session)
                .shouldBeRight()

        test("elimina la sesion del plan") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))

            DeleteSessionCommand(repository, lookup).execute(coach, plan.id, session.id).shouldBeRight()

            repository.findById(club, plan.id)!!.sessions shouldBe emptyList()
        }

        test("una sesion que no existe en el plan da SessionNotFound") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))

            DeleteSessionCommand(repository, lookup)
                .execute(coach, plan.id, SessionId.new())
                .shouldBeLeft(PlanificacionError.SessionNotFound)
        }

        test("un entrenador sin relacion con el grupo recibe Forbidden y no borra nada") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(emptySet())

            DeleteSessionCommand(repository, lookup)
                .execute(coach, plan.id, session.id)
                .shouldBeLeft(PlanificacionError.Forbidden)

            repository.findById(club, plan.id)!!.sessions shouldBe listOf(session)
        }
    })
