package com.runcriticon.planificacion.application.usecases.personalizations

import com.runcriticon.planificacion.api.events.PersonalizacionRetirada
import com.runcriticon.planificacion.application.usecases.plans.InMemoryCoachGroupLookup
import com.runcriticon.planificacion.application.usecases.plans.InMemoryWeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.Personalization
import com.runcriticon.planificacion.domain.PlanStatus
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.planificacion.domain.SessionOverride
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.util.UUID

class RemovePersonalizationCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val monday = LocalDate.of(2026, 8, 17)
        val student = PersonId.of(UUID.randomUUID())

        fun draftWithPersonalization(): WeeklyPlan {
            val plan = WeeklyPlan.createDraft(club, group, PersonId.of(coach.userId), monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val withSession = plan.addSession(session).shouldBeRight()
            val personalization =
                Personalization
                    .create(
                        sessionId = session.id,
                        studentId = student,
                        override = SessionOverride.create(type = SessionType.DESCANSO).shouldBeRight(),
                    ).shouldBeRight()
            return withSession.setPersonalization(personalization).shouldBeRight()
        }

        test("retirar una personalizacion en borrador la quita y no emite evento") {
            val plan = draftWithPersonalization()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = RemovePersonalizationCommand(repository, lookup, eventPublisher)

            command.execute(coach, plan.id, plan.sessions.single().id, student).shouldBeRight()

            repository.findById(club, plan.id)!!.personalizations shouldBe emptyList()
            verify(exactly = 0) { eventPublisher.publishEvent(any<PersonalizacionRetirada>()) }
        }

        test("retirar una personalizacion en un plan publicado emite PersonalizacionRetirada con la sesion base") {
            val plan = draftWithPersonalization().copy(status = PlanStatus.PUBLICADO)
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<PersonalizacionRetirada>()
            val command = RemovePersonalizationCommand(repository, lookup, eventPublisher)

            command.execute(coach, plan.id, plan.sessions.single().id, student).shouldBeRight()

            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.aggregateId shouldBe plan.id.value
            slot.captured.alumnoId shouldBe student.value
            slot.captured.baseSession.tipo shouldBe SessionType.RODAJE.name
        }

        test("retirar una personalizacion que no existe devuelve PersonalizationNotFound") {
            val plan = WeeklyPlan.createDraft(club, group, PersonId.of(coach.userId), monday).shouldBeRight()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = RemovePersonalizationCommand(repository, lookup, eventPublisher)

            command
                .execute(coach, plan.id, SessionId.new(), student)
                .shouldBeLeft(PlanificacionError.PersonalizationNotFound)
        }

        test("un entrenador sin relacion con el grupo recibe Forbidden y no retira nada") {
            val plan = draftWithPersonalization()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(emptySet())
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = RemovePersonalizationCommand(repository, lookup, eventPublisher)

            command
                .execute(coach, plan.id, plan.sessions.single().id, student)
                .shouldBeLeft(PlanificacionError.Forbidden)

            repository.findById(club, plan.id)!!.personalizations shouldBe listOf(plan.personalizations.single())
        }
    })
