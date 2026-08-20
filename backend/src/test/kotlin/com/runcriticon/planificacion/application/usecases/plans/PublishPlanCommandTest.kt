package com.runcriticon.planificacion.application.usecases.plans

import com.runcriticon.auditoria.api.events.AccesoDenegado
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanStatus
import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.util.UUID

class PublishPlanCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val monday = LocalDate.of(2026, 8, 17)
        val student1 = PersonId.of(UUID.randomUUID())
        val student2 = PersonId.of(UUID.randomUUID())

        fun draftWithSession(): WeeklyPlan {
            val plan = WeeklyPlan.createDraft(club, group, PersonId.of(coach.userId), monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            return plan.addSession(session).shouldBeRight()
        }

        test("un entrenador con relacion publica el plan y congela el snapshot") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student1, student2)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(0L), eventPublisher)

            val result = command.execute(coach, plan.id).shouldBeRight()

            result.plan.status shouldBe PlanStatus.PUBLICADO
            result.studentsInSnapshot shouldBe 2
            repository.published.single().third shouldContainExactlyInAnyOrder setOf(student1, student2)
        }

        test("un entrenador sin relacion con el grupo recibe Forbidden y no publica nada") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(emptySet())
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student1)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(0L), eventPublisher)

            command.execute(coach, plan.id).shouldBeLeft(PlanificacionError.Forbidden)

            repository.published.size shouldBe 0
        }

        test("el alumno no puede publicar y no llega a comprobar la relacion con el grupo") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student1)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val student = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)
            val command = PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(0L), eventPublisher)

            command.execute(student, plan.id).shouldBeLeft(PlanificacionError.Forbidden)

            lookup.calls.size shouldBe 0
            repository.published.size shouldBe 0
        }

        test("una proyeccion atrasada 60s o mas rechaza con ProjectionStale y no publica nada") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student1)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(60L), eventPublisher)

            val error = command.execute(coach, plan.id).shouldBeLeft()

            error.shouldBe(PlanificacionError.ProjectionStale(60L))
            repository.published.size shouldBe 0
        }

        test("un plan sin sesiones no se puede publicar") {
            val plan = WeeklyPlan.createDraft(club, group, PersonId.of(coach.userId), monday).shouldBeRight()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection()
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(0L), eventPublisher)

            command.execute(coach, plan.id).shouldBeLeft(PlanificacionError.NoSessions)

            repository.published.size shouldBe 0
        }

        test("un plan ya publicado no se puede republicar") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan.copy(status = PlanStatus.PUBLICADO)))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student1)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(0L), eventPublisher)

            command.execute(coach, plan.id).shouldBeLeft(PlanificacionError.PlanAlreadyPublished)

            repository.published.size shouldBe 0
        }

        test("publicar emite PlanPublicado auto-contenido con el snapshot y las sesiones") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student1, student2)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<PlanPublicado>()
            val command = PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(0L), eventPublisher)

            command.execute(coach, plan.id).shouldBeRight()

            verify { eventPublisher.publishEvent(capture(slot)) }
            val event = slot.captured
            event.aggregateId shouldBe plan.id.value
            event.grupoId shouldBe group.value
            event.clubId shouldBe club.value
            event.snapshotAlumnos shouldContainExactlyInAnyOrder listOf(student1.value, student2.value)
            event.sesiones.map { it.tipo } shouldBe listOf(SessionType.RODAJE.name)
        }

        test("el alumno rechazado por RBAC emite AccesoDenegado con motivo RBAC") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student1)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<AccesoDenegado>()
            val student = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)
            val command = PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(0L), eventPublisher)

            command.execute(student, plan.id).shouldBeLeft(PlanificacionError.Forbidden)

            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.recurso shouldBe "PLAN:PUBLISH"
            slot.captured.motivo shouldBe "RBAC"
            slot.captured.aggregateId shouldBe student.userId
            slot.captured.actorId shouldBe student.userId
        }

        test("un entrenador sin relacion con el grupo emite AccesoDenegado con motivo NotCoachOfGroup") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(emptySet())
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student1)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<AccesoDenegado>()
            val command = PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(0L), eventPublisher)

            command.execute(coach, plan.id).shouldBeLeft(PlanificacionError.Forbidden)

            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.motivo shouldBe "NotCoachOfGroup"
            slot.captured.aggregateId shouldBe plan.id.value
            slot.captured.sujetoId shouldBe group.value
        }

        test("una proyeccion atrasada emite AccesoDenegado con el lag en el motivo") {
            val plan = draftWithSession()
            val repository = InMemoryWeeklyPlanRepository(listOf(plan))
            val lookup = InMemoryCoachGroupLookup(setOf(PersonId.of(coach.userId) to group))
            val members = InMemoryGroupMembersProjection(mapOf(group to setOf(student1)))
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<AccesoDenegado>()
            val command = PublishPlanCommand(repository, lookup, members, FakeProjectionFreshness(60L), eventPublisher)

            command.execute(coach, plan.id).shouldBeLeft(PlanificacionError.ProjectionStale(60L))

            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.motivo shouldBe "ProjectionStale(lag=60s)"
            slot.captured.aggregateId shouldBe plan.id.value
        }
    })
