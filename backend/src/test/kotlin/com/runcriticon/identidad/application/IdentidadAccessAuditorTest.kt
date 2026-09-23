package com.runcriticon.identidad.application

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
 * Cubre el punto único que construye [AccesoDenegado] para los 12 casos de uso RBAC de `identidad` cubiertos tras
 * extender su emisión al resto de casos de uso: mismo criterio de verificación que `ClubTaxonomiaAccessAuditorTest`.
 */
class IdentidadAccessAuditorTest :
    FunSpec({
        val actor = Principal(userId = UUID.randomUUID(), clubId = UUID.randomUUID(), role = Role.ENTRENADOR)

        test("publica AccesoDenegado con el recurso:accion, motivo RBAC y sin sujeto") {
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<AccesoDenegado>()
            val auditor = IdentidadAccessAuditor(eventPublisher)

            auditor.denegado(actor, Resource.STUDENT, Action.INVITE)

            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.recurso shouldBe "STUDENT:INVITE"
            slot.captured.motivo shouldBe "RBAC"
            slot.captured.aggregateId shouldBe actor.userId
            slot.captured.actorId shouldBe actor.userId
            slot.captured.clubId shouldBe actor.clubId
            slot.captured.sujetoId shouldBe null
        }
    })
