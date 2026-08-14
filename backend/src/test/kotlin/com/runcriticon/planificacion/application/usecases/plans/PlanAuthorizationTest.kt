package com.runcriticon.planificacion.application.usecases.plans

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
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.util.UUID

/**
 * `PLAN:CREATE` es solo del `ENTRENADOR` (ver comentario en `AuthorizationMatrix`) — a diferencia de la mayoría de
 * operaciones de grupo de `clubtaxonomia`, que comparten ADMIN y ENTRENADOR. Test aparte, mismo criterio que
 * `GroupCoachAssignmentAuthorizationTest`: el permiso no es simétrico entre roles.
 */
class PlanAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val monday = LocalDate.of(2026, 8, 17)

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        listOf(Role.ADMIN, Role.ALUMNO).forEach { role ->
            test("$role no puede crear un plan, y no se toca la base") {
                val repository = InMemoryWeeklyPlanRepository()
                val actor = principal(role)
                val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(actor.userId) to group))

                withClue(role.toString()) {
                    CreateDraftPlanCommand(repository, lookup)
                        .execute(actor, group.value, monday)
                        .shouldBeLeft(PlanificacionError.Forbidden)
                }

                lookup.calls.size shouldBe 0
                repository.saved.size shouldBe 0
            }
        }

        test("el entrenador con relacion con el grupo puede crear el plan") {
            val repository = InMemoryWeeklyPlanRepository()
            val actor = principal(Role.ENTRENADOR)
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(actor.userId) to group))

            CreateDraftPlanCommand(repository, lookup)
                .execute(actor, group.value, monday)
                .shouldBeRight()
        }

        listOf(Role.ADMIN, Role.ALUMNO).forEach { role ->
            test("$role no puede publicar un plan, y no se toca la base") {
                val coach = PersonId.of(UUID.randomUUID())
                val plan =
                    WeeklyPlan
                        .createDraft(club, group, coach, monday)
                        .shouldBeRight()
                        .let { draft ->
                            draft.addSession(Session.create(day = monday, type = SessionType.DESCANSO).shouldBeRight())
                        }.shouldBeRight()
                val repository = InMemoryWeeklyPlanRepository(listOf(plan))
                val actor = principal(role)
                val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(actor.userId) to group))
                val members = InMemoryGroupMembersProjection()
                val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

                withClue(role.toString()) {
                    PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(0L), eventPublisher)
                        .execute(actor, plan.id)
                        .shouldBeLeft(PlanificacionError.Forbidden)
                }

                lookup.calls.size shouldBe 0
                repository.published.size shouldBe 0
            }
        }
    })
