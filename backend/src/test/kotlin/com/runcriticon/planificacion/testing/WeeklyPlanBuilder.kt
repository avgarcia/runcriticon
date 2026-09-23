package com.runcriticon.planificacion.testing

import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.TestClubs
import io.kotest.assertions.arrow.core.shouldBeRight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * Builder de [WeeklyPlan] (`testing-de-modulos.md` §8), sobre `WeeklyPlan.createDraft` + `Session.create`. Por
 * defecto crea un borrador sin sesiones en el lunes de la semana actual; [withSessions] añade sesiones de
 * `RODAJE` en días consecutivos desde el lunes.
 */
class WeeklyPlanBuilder {
    private var clubId: ClubId = TestClubs.newClub()
    private var groupId: GroupId = GroupId.of(UUID.randomUUID())
    private var coachId: PersonId = PersonId.of(UUID.randomUUID())
    private var week: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private var id: PlanId = PlanId.new()
    private var sessionCount: Int = 0
    private var published: Boolean = false

    fun inClub(clubId: ClubId) = apply { this.clubId = clubId }

    fun ofGroup(groupId: GroupId) = apply { this.groupId = groupId }

    fun ofCoach(coachId: PersonId) = apply { this.coachId = coachId }

    /** [week] debe ser lunes — misma invariante que `WeeklyPlan.createDraft`. */
    fun forWeek(week: LocalDate) = apply { this.week = week }

    fun withId(id: PlanId) = apply { this.id = id }

    fun withSessions(count: Int) = apply { this.sessionCount = count }

    fun draft() = apply { this.published = false }

    fun published() = apply { this.published = true }

    fun build(): WeeklyPlan {
        var plan = WeeklyPlan.createDraft(clubId, groupId, coachId, week, id).shouldBeRight()
        repeat(sessionCount) { i ->
            val session = Session.create(day = week.plusDays(i.toLong()), type = SessionType.RODAJE).shouldBeRight()
            plan = plan.addSession(session).shouldBeRight()
        }
        if (published) {
            plan = plan.publish().shouldBeRight()
        }
        return plan
    }
}
