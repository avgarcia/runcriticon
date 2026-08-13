package com.runcriticon.planificacion.application.usecases.plans

import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
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

class ListDraftPlansQueryTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val plan =
            WeeklyPlan.createDraft(club, group, PersonId.of(coach.userId), LocalDate.of(2026, 8, 17)).shouldBeRight()

        test("un entrenador con relacion con el grupo ve sus planes en borrador") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))

            val plans = ListDraftPlansQuery(repository, lookup).execute(coach, group.value).shouldBeRight()

            plans shouldBe listOf(plan)
        }

        test("un entrenador sin relacion con el grupo ve lista vacia, no Forbidden") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(emptySet())

            val plans = ListDraftPlansQuery(repository, lookup).execute(coach, group.value).shouldBeRight()

            plans shouldBe emptyList()
        }

        test("el alumno no puede listar planes") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val student = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)

            ListDraftPlansQuery(repository, lookup)
                .execute(student, group.value)
                .shouldBeLeft(PlanificacionError.Forbidden)
        }
    })
