package com.runcriticon.planificacion.application.usecases.sessions

import com.runcriticon.planificacion.application.usecases.plans.InMemoryCoachGroupLookup
import com.runcriticon.planificacion.application.usecases.plans.InMemoryWeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.UUID

/**
 * `PLAN:UPDATE` (mutar sesiones) es solo del `ENTRENADOR`, igual que `PLAN:CREATE`/`PLAN:LIST` — mismo criterio
 * que `PlanAuthorizationTest`. El caso propio de esta suite, sin equivalente en la de creación: un entrenador que
 * fue el creador del plan pero **ya no tiene relación con el grupo** (decisión 5 del ticket, LAL-24) — no basta
 * con `plan.coachId == actor.userId`, hay que revalidar `CoachGroupLookup` en cada mutación.
 */
class SessionAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val monday = LocalDate.of(2026, 8, 17)

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        listOf(Role.ADMIN, Role.ALUMNO).forEach { role ->
            test("$role no puede anadir una sesion, y no se toca la base") {
                val actor = principal(role)
                val plan = WeeklyPlan.createDraft(club, group, PersonId.of(actor.userId), monday).shouldBeRight()
                val repository = InMemoryWeeklyPlanRepository(listOf(plan))
                val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(actor.userId) to group))

                withClue(role.toString()) {
                    AddSessionCommand(repository, lookup)
                        .execute(actor, plan.id, monday.plusDays(1), SessionType.RODAJE, null, null, null)
                        .shouldBeLeft(PlanificacionError.Forbidden)
                }

                lookup.calls.size shouldBe 0
                repository.findById(club, plan.id)!!.sessions shouldBe emptyList()
            }
        }

        test("un entrenador expulsado del grupo despues de crear el plan pierde acceso a mutarlo") {
            val actor = principal(Role.ENTRENADOR)
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val plan =
                WeeklyPlan
                    .createDraft(club, group, PersonId.of(actor.userId), monday)
                    .shouldBeRight()
                    .addSession(session)
                    .shouldBeRight()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            // El entrenador es el dueño original del plan (`plan.coachId == actor.userId`), pero la
            // proyección `miembro_grupo` ya no lo tiene como entrenador del grupo — mismo motivo por el que
            // el caso de uso no puede quedarse solo con la comprobación de propiedad.
            val lookup = InMemoryCoachGroupLookup(emptySet())

            AddSessionCommand(repository, lookup)
                .execute(actor, plan.id, monday.plusDays(2), SessionType.SERIES, null, null, null)
                .shouldBeLeft(PlanificacionError.Forbidden)

            UpdateSessionCommand(repository, lookup)
                .execute(actor, plan.id, session.id, SessionType.TEMPO, null, null, null)
                .shouldBeLeft(PlanificacionError.Forbidden)

            DeleteSessionCommand(repository, lookup)
                .execute(actor, plan.id, session.id)
                .shouldBeLeft(PlanificacionError.Forbidden)

            repository.findById(club, plan.id)!!.sessions shouldBe listOf(session)
        }
    })
