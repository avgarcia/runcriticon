package com.runcriticon.planificacion.application.usecases.personalizations

import com.runcriticon.planificacion.api.events.PersonalizacionAplicada
import com.runcriticon.planificacion.application.usecases.plans.InMemoryCoachGroupLookup
import com.runcriticon.planificacion.application.usecases.plans.InMemoryGroupMembersProjection
import com.runcriticon.planificacion.application.usecases.plans.InMemoryWeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanStatus
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
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.util.UUID

class SetPersonalizationCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val monday = LocalDate.of(2026, 8, 17)
        val student = PersonId.of(UUID.randomUUID())

        fun draftWithSession(): WeeklyPlan {
            val plan = WeeklyPlan.createDraft(club, group, PersonId.of(coach.userId), monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            return plan.addSession(session).shouldBeRight()
        }

        test("personalizar un plan en borrador con el alumno en el grupo funciona y no emite evento") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = SetPersonalizationCommand(repository, lookup, members, eventPublisher)

            val updated =
                command
                    .execute(
                        actor = coach,
                        planId = plan.id,
                        sessionId = plan.sessions.single().id,
                        studentId = student,
                        type = SessionType.DESCANSO,
                        volume = null,
                        pace = null,
                        notes = null,
                        messageToStudent = "Descansa hoy",
                    ).shouldBeRight()

            updated.personalizations.single().studentId shouldBe student
            verify(exactly = 0) { eventPublisher.publishEvent(any<PersonalizacionAplicada>()) }
        }

        test("personalizar un plan publicado con el alumno en el snapshot emite PersonalizacionAplicada") {
            val plan = draftWithSession().copy(status = PlanStatus.PUBLICADO)
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            repository.published += Triple(club, plan.id, setOf(student))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection()
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<PersonalizacionAplicada>()
            val command = SetPersonalizationCommand(repository, lookup, members, eventPublisher)

            command
                .execute(
                    actor = coach,
                    planId = plan.id,
                    sessionId = plan.sessions.single().id,
                    studentId = student,
                    type = SessionType.DESCANSO,
                    volume = null,
                    pace = null,
                    notes = null,
                    messageToStudent = "Descansa hoy",
                ).shouldBeRight()

            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.aggregateId shouldBe plan.id.value
            slot.captured.alumnoId shouldBe student.value
            slot.captured.dia shouldBe plan.sessions.single().day
            slot.captured.mensajeAlAlumno shouldBe "Descansa hoy"
        }

        test("un entrenador sin relacion con el grupo recibe Forbidden y no persiste nada") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(emptySet())
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = SetPersonalizationCommand(repository, lookup, members, eventPublisher)

            command
                .execute(
                    actor = coach,
                    planId = plan.id,
                    sessionId = plan.sessions.single().id,
                    studentId = student,
                    type = SessionType.DESCANSO,
                    volume = null,
                    pace = null,
                    notes = null,
                    messageToStudent = null,
                ).shouldBeLeft(PlanificacionError.Forbidden)

            repository.findById(club, plan.id)!!.personalizations shouldBe emptyList()
        }

        test("un alumno fuera del grupo en un plan en borrador recibe StudentNotInPlan") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection(mapOf(group to emptySet()))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = SetPersonalizationCommand(repository, lookup, members, eventPublisher)

            command
                .execute(
                    actor = coach,
                    planId = plan.id,
                    sessionId = plan.sessions.single().id,
                    studentId = student,
                    type = SessionType.DESCANSO,
                    volume = null,
                    pace = null,
                    notes = null,
                    messageToStudent = null,
                ).shouldBeLeft(PlanificacionError.StudentNotInPlan)
        }

        test("un alumno fuera del snapshot en un plan publicado recibe StudentNotInPlan") {
            val plan = draftWithSession().copy(status = PlanStatus.PUBLICADO)
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            repository.published += Triple(club, plan.id, emptySet())
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection()
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = SetPersonalizationCommand(repository, lookup, members, eventPublisher)

            command
                .execute(
                    actor = coach,
                    planId = plan.id,
                    sessionId = plan.sessions.single().id,
                    studentId = student,
                    type = SessionType.DESCANSO,
                    volume = null,
                    pace = null,
                    notes = null,
                    messageToStudent = null,
                ).shouldBeLeft(PlanificacionError.StudentNotInPlan)
        }

        test("una sesion que no existe en el plan devuelve SessionNotFound") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = SetPersonalizationCommand(repository, lookup, members, eventPublisher)

            command
                .execute(
                    actor = coach,
                    planId = plan.id,
                    sessionId = SessionId.new(),
                    studentId = student,
                    type = SessionType.DESCANSO,
                    volume = null,
                    pace = null,
                    notes = null,
                    messageToStudent = null,
                ).shouldBeLeft(PlanificacionError.SessionNotFound)
        }
    })
