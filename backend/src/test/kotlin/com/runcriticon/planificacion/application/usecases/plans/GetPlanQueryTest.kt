package com.runcriticon.planificacion.application.usecases.plans

import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.PlanificacionError
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

class GetPlanQueryTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val plan =
            WeeklyPlan.createDraft(club, group, PersonId.of(coach.userId), LocalDate.of(2026, 8, 17)).shouldBeRight()

        test("devuelve el plan completo con id existente") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))

            GetPlanQuery(repository).execute(coach, plan.id).shouldBeRight() shouldBe plan
        }

        test("un plan inexistente da Forbidden, no NotFound") {
            val repository = InMemoryWeeklyPlanRepository()

            GetPlanQuery(repository)
                .execute(coach, PlanId.new())
                .shouldBeLeft(PlanificacionError.Forbidden)
        }

        test("un plan de otro club da Forbidden") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val otherClub = ClubId.of(UUID.randomUUID())
            val outsider = Principal(userId = UUID.randomUUID(), clubId = otherClub.value, role = Role.ENTRENADOR)

            GetPlanQuery(repository)
                .execute(outsider, plan.id)
                .shouldBeLeft(PlanificacionError.Forbidden)
        }

        test("el alumno no puede consultar el detalle de un plan") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val student = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)

            GetPlanQuery(repository)
                .execute(student, plan.id)
                .shouldBeLeft(PlanificacionError.Forbidden)
        }
    })
