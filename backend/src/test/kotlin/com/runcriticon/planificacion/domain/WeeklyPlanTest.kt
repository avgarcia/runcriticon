package com.runcriticon.planificacion.domain

import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate
import java.util.UUID

class WeeklyPlanTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val group = GroupId.of(UUID.randomUUID())
        val coach = PersonId.of(UUID.randomUUID())
        val monday = LocalDate.of(2026, 8, 17)

        test("crear un borrador con el lunes de la semana lo deja en BORRADOR") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()

            plan.status shouldBe PlanStatus.BORRADOR
            plan.clubId shouldBe club
            plan.groupId shouldBe group
            plan.coachId shouldBe coach
            plan.sessions shouldBe emptyList()
            plan.personalizations shouldBe emptyList()
        }

        test("un dia que no es lunes se rechaza") {
            val tuesday = monday.plusDays(1)

            val error = WeeklyPlan.createDraft(club, group, coach, tuesday).shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "semana"
        }

        test("anadir una sesion dentro de la semana la deja en el plan") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(2), type = SessionType.RODAJE).shouldBeRight()

            val updated = plan.addSession(session).shouldBeRight()

            updated.sessions shouldBe listOf(session)
        }

        test("una sesion el propio lunes o el domingo (limites de la semana) se acepta") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val onMonday = Session.create(day = monday, type = SessionType.DESCANSO).shouldBeRight()
            val onSunday = Session.create(day = monday.plusDays(6), type = SessionType.DESCANSO).shouldBeRight()

            val afterMonday = plan.addSession(onMonday).shouldBeRight()
            val afterBoth = afterMonday.addSession(onSunday).shouldBeRight()

            afterBoth.sessions shouldBe listOf(onMonday, onSunday)
        }

        test("una sesion fuera de la semana del plan se rechaza") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val nextMonday = Session.create(day = monday.plusDays(7), type = SessionType.DESCANSO).shouldBeRight()

            val error = plan.addSession(nextMonday).shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "dia"
        }

        test("dos sesiones el mismo dia se rechazan") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val first = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val second = Session.create(day = monday.plusDays(1), type = SessionType.SERIES).shouldBeRight()
            val withFirst = plan.addSession(first).shouldBeRight()

            val error = withFirst.addSession(second).shouldBeLeft()

            error shouldBe PlanificacionError.DuplicateSessionDay
        }

        test("actualizar una sesion existente la sustituye") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val original = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val withSession = plan.addSession(original).shouldBeRight()
            val edited =
                Session
                    .create(id = original.id, day = original.day, type = SessionType.SERIES)
                    .shouldBeRight()

            val updated = withSession.updateSession(edited).shouldBeRight()

            updated.sessions shouldBe listOf(edited)
        }

        test("actualizar una sesion que no existe en el plan falla") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val ghost = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()

            val error = plan.updateSession(ghost).shouldBeLeft()

            error shouldBe PlanificacionError.SessionNotFound
        }

        test("eliminar una sesion existente la quita del plan") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val withSession = plan.addSession(session).shouldBeRight()

            val updated = withSession.removeSession(session.id).shouldBeRight()

            updated.sessions shouldBe emptyList()
        }

        test("eliminar una sesion que no existe en el plan falla") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()

            val error = plan.removeSession(SessionId.new()).shouldBeLeft()

            error shouldBe PlanificacionError.SessionNotFound
        }

        test("publicar un plan con sesiones lo deja en PUBLICADO") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val withSession = plan.addSession(session).shouldBeRight()

            val published = withSession.publish().shouldBeRight()

            published.status shouldBe PlanStatus.PUBLICADO
        }

        test("publicar un plan sin sesiones falla") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()

            val error = plan.publish().shouldBeLeft()

            error shouldBe PlanificacionError.NoSessions
        }

        test("publicar un plan ya publicado falla") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val published =
                plan
                    .addSession(session)
                    .shouldBeRight()
                    .publish()
                    .shouldBeRight()

            val error = published.publish().shouldBeLeft()

            error shouldBe PlanificacionError.PlanAlreadyPublished
        }

        test("anadir una sesion a un plan publicado falla") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val published =
                plan
                    .addSession(session)
                    .shouldBeRight()
                    .publish()
                    .shouldBeRight()
            val another = Session.create(day = monday.plusDays(2), type = SessionType.RODAJE).shouldBeRight()

            val error = published.addSession(another).shouldBeLeft()

            error shouldBe PlanificacionError.PlanAlreadyPublished
        }

        test("actualizar una sesion de un plan publicado falla") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val published =
                plan
                    .addSession(session)
                    .shouldBeRight()
                    .publish()
                    .shouldBeRight()
            val edited = Session.create(id = session.id, day = session.day, type = SessionType.SERIES).shouldBeRight()

            val error = published.updateSession(edited).shouldBeLeft()

            error shouldBe PlanificacionError.PlanAlreadyPublished
        }

        test("eliminar una sesion de un plan publicado falla") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val published =
                plan
                    .addSession(session)
                    .shouldBeRight()
                    .publish()
                    .shouldBeRight()

            val error = published.removeSession(session.id).shouldBeLeft()

            error shouldBe PlanificacionError.PlanAlreadyPublished
        }

        // LAL-26: personalizar una sesión para un alumno concreto.

        test("personalizar una sesion existente la anade al plan") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val withSession = plan.addSession(session).shouldBeRight()
            val student = PersonId.of(UUID.randomUUID())
            val override = SessionOverride.create(type = SessionType.DESCANSO).shouldBeRight()
            val personalization =
                Personalization.create(sessionId = session.id, studentId = student, override = override).shouldBeRight()

            val updated = withSession.setPersonalization(personalization).shouldBeRight()

            updated.personalizations shouldBe listOf(personalization)
        }

        test("personalizar una sesion inexistente en el plan falla") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val student = PersonId.of(UUID.randomUUID())
            val override = SessionOverride.create(type = SessionType.DESCANSO).shouldBeRight()
            val personalization =
                Personalization
                    .create(sessionId = SessionId.new(), studentId = student, override = override)
                    .shouldBeRight()

            val error = plan.setPersonalization(personalization).shouldBeLeft()

            error shouldBe PlanificacionError.SessionNotFound
        }

        test("volver a personalizar la misma sesion y alumno sustituye, no acumula") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val withSession = plan.addSession(session).shouldBeRight()
            val student = PersonId.of(UUID.randomUUID())
            val first =
                Personalization
                    .create(
                        sessionId = session.id,
                        studentId = student,
                        override = SessionOverride.create(type = SessionType.DESCANSO).shouldBeRight(),
                        messageToStudent = "primer mensaje",
                    ).shouldBeRight()
            val withFirst = withSession.setPersonalization(first).shouldBeRight()
            val second =
                Personalization
                    .create(
                        sessionId = session.id,
                        studentId = student,
                        override = SessionOverride.create(type = SessionType.RODAJE).shouldBeRight(),
                        messageToStudent = "segundo mensaje",
                    ).shouldBeRight()

            val updated = withFirst.setPersonalization(second).shouldBeRight()

            updated.personalizations shouldBe listOf(second)
        }

        test("personalizar una sesion de un plan ya publicado funciona") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val published =
                plan
                    .addSession(session)
                    .shouldBeRight()
                    .publish()
                    .shouldBeRight()
            val student = PersonId.of(UUID.randomUUID())
            val personalization =
                Personalization
                    .create(
                        sessionId = session.id,
                        studentId = student,
                        override = SessionOverride.create(type = SessionType.DESCANSO).shouldBeRight(),
                    ).shouldBeRight()

            val updated = published.setPersonalization(personalization).shouldBeRight()

            updated.status shouldBe PlanStatus.PUBLICADO
            updated.personalizations shouldBe listOf(personalization)
        }

        test("retirar una personalizacion existente la quita del plan") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val withSession = plan.addSession(session).shouldBeRight()
            val student = PersonId.of(UUID.randomUUID())
            val personalization =
                Personalization
                    .create(
                        sessionId = session.id,
                        studentId = student,
                        override = SessionOverride.create(type = SessionType.DESCANSO).shouldBeRight(),
                    ).shouldBeRight()
            val withPersonalization = withSession.setPersonalization(personalization).shouldBeRight()

            val updated = withPersonalization.removePersonalization(session.id, student).shouldBeRight()

            updated.personalizations shouldBe emptyList()
        }

        test("retirar una personalizacion que no existe falla") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val student = PersonId.of(UUID.randomUUID())

            val error = plan.removePersonalization(SessionId.new(), student).shouldBeLeft()

            error shouldBe PlanificacionError.PersonalizationNotFound
        }

        test("resolver la sesion de un alumno sin personalizacion devuelve la base") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val withSession = plan.addSession(session).shouldBeRight()
            val student = PersonId.of(UUID.randomUUID())

            withSession.resolveSessionForStudent(session.day, student) shouldBe session
        }

        test("resolver la sesion de un alumno con personalizacion devuelve el override") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val withSession = plan.addSession(session).shouldBeRight()
            val student = PersonId.of(UUID.randomUUID())
            val override = SessionOverride.create(type = SessionType.DESCANSO).shouldBeRight()
            val personalization =
                Personalization.create(sessionId = session.id, studentId = student, override = override).shouldBeRight()
            val withPersonalization = withSession.setPersonalization(personalization).shouldBeRight()

            val resolved = withPersonalization.resolveSessionForStudent(session.day, student)

            resolved shouldBe session.copy(type = SessionType.DESCANSO)
        }

        test("resolver la sesion de un alumno con personalizacion de otro alumno devuelve la base") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
            val withSession = plan.addSession(session).shouldBeRight()
            val personalizedStudent = PersonId.of(UUID.randomUUID())
            val otherStudent = PersonId.of(UUID.randomUUID())
            val override = SessionOverride.create(type = SessionType.DESCANSO).shouldBeRight()
            val personalization =
                Personalization
                    .create(sessionId = session.id, studentId = personalizedStudent, override = override)
                    .shouldBeRight()
            val withPersonalization = withSession.setPersonalization(personalization).shouldBeRight()

            withPersonalization.resolveSessionForStudent(session.day, otherStudent) shouldBe session
        }

        test("resolver un dia sin sesion devuelve null") {
            val plan = WeeklyPlan.createDraft(club, group, coach, monday).shouldBeRight()
            val student = PersonId.of(UUID.randomUUID())

            plan.resolveSessionForStudent(monday.plusDays(3), student) shouldBe null
        }
    })
