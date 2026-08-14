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

class UpdateSessionCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val monday = LocalDate.of(2026, 8, 17)
        val originalSession = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
        val plan =
            WeeklyPlan
                .createDraft(club, group, PersonId.of(coach.userId), monday)
                .shouldBeRight()
                .addSession(originalSession)
                .shouldBeRight()

        test("sustituye tipo, volumen, ritmo y notas sin tocar el dia") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))

            val updated =
                UpdateSessionCommand(repository, lookup)
                    .execute(coach, plan.id, originalSession.id, SessionType.SERIES, null, null, "cambio de plan")
                    .shouldBeRight()

            updated.day shouldBe originalSession.day
            updated.type shouldBe SessionType.SERIES
            updated.notes shouldBe "cambio de plan"
            repository.findById(club, plan.id)!!.sessions shouldBe listOf(updated)
        }

        test("una sesion que no existe en el plan da SessionNotFound") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))

            UpdateSessionCommand(repository, lookup)
                .execute(coach, plan.id, SessionId.new(), SessionType.SERIES, null, null, null)
                .shouldBeLeft(PlanificacionError.SessionNotFound)
        }

        test("un entrenador sin relacion con el grupo recibe Forbidden") {
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(emptySet())

            UpdateSessionCommand(repository, lookup)
                .execute(coach, plan.id, originalSession.id, SessionType.SERIES, null, null, null)
                .shouldBeLeft(PlanificacionError.Forbidden)

            repository.findById(club, plan.id)!!.sessions shouldBe listOf(originalSession)
        }
    })
