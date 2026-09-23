package com.runcriticon.planificacion.testing

import com.runcriticon.planificacion.domain.PlanStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek

class WeeklyPlanBuilderTest :
    FunSpec({
        test("WeeklyPlanBuilder por defecto crea un borrador sin sesiones, en lunes") {
            val plan = WeeklyPlanBuilder().build()

            plan.status shouldBe PlanStatus.BORRADOR
            plan.sessions shouldBe emptyList()
            plan.week.dayOfWeek shouldBe DayOfWeek.MONDAY
        }

        test("WeeklyPlanBuilder.withSessions añade sesiones en días consecutivos desde el lunes") {
            val plan = WeeklyPlanBuilder().withSessions(3).build()

            plan.sessions.size shouldBe 3
            plan.sessions.map { it.day } shouldBe (0..2).map { plan.week.plusDays(it.toLong()) }
        }

        test("WeeklyPlanBuilder.published deja el plan publicado") {
            val plan = WeeklyPlanBuilder().withSessions(1).published().build()

            plan.status shouldBe PlanStatus.PUBLICADO
        }
    })
