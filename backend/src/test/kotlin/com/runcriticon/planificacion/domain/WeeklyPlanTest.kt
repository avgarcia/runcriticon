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
    })
