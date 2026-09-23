package com.runcriticon.planificacion.application

import com.runcriticon.shared.api.events.AccesoDenegado
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

/**
 * Cubre el publicador que usan los seis casos de uso de `planificacion` sin `denegado(...)` propio:
 * `AddSessionCommand`, `UpdateSessionCommand`, `DeleteSessionCommand`, `CreateDraftPlanCommand`, `GetPlanQuery`,
 * `ListDraftPlansQuery`. A diferencia de `ClubTaxonomiaAccessAuditor`, aquí `aggregateId`/`sujetoId` varían por
 * llamada — mismo criterio que el `denegado(...)` privado de `PublishPlanCommand`.
 */
class PlanificacionAccessAuditorTest :
    FunSpec({
        val actor = Principal(userId = UUID.randomUUID(), clubId = UUID.randomUUID(), role = Role.ENTRENADOR)

        test("publica AccesoDenegado con el recurso:accion y el aggregateId/motivo/sujeto pedidos") {
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<AccesoDenegado>()
            val auditor = PlanificacionAccessAuditor(eventPublisher)
            val groupId = UUID.randomUUID()
            val planId = UUID.randomUUID()

            auditor.denegado(
                actor,
                Resource.PLAN,
                Action.UPDATE,
                aggregateId = planId,
                motivo = "NotCoachOfGroup",
                sujetoId = groupId,
            )

            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.recurso shouldBe "PLAN:UPDATE"
            slot.captured.motivo shouldBe "NotCoachOfGroup"
            slot.captured.aggregateId shouldBe planId
            slot.captured.sujetoId shouldBe groupId
            slot.captured.actorId shouldBe actor.userId
            slot.captured.clubId shouldBe actor.clubId
        }

        test("sujetoId es null por defecto") {
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<AccesoDenegado>()
            val auditor = PlanificacionAccessAuditor(eventPublisher)

            auditor.denegado(actor, Resource.PLAN, Action.LIST, aggregateId = actor.userId, motivo = "RBAC")

            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.sujetoId shouldBe null
        }
    })
