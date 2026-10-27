package com.runcriticon.seguimiento.application

import com.runcriticon.shared.api.events.AccesoDenegado
import com.runcriticon.shared.autorizacion.model.Principal
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.context.ApplicationEventPublisher

/**
 * [SeguimientoAccessAuditor] real sobre un publicador que guarda lo publicado: los tests de autorización afirman con
 * él que cada rechazo deja su [AccesoDenegado] (ADR-0009 D15-D16), no solo que devuelve `Forbidden`.
 */
class RecordingAccessAuditor {
    val denied: MutableList<AccesoDenegado> = mutableListOf()

    val auditor: SeguimientoAccessAuditor =
        SeguimientoAccessAuditor(ApplicationEventPublisher { event -> if (event is AccesoDenegado) denied += event })

    /** Exactamente un rechazo de RBAC de [actor] sobre [recurso] (`RECURSO:ACCION`). */
    fun shouldHaveDeniedRbac(
        actor: Principal,
        recurso: String,
    ) {
        denied shouldHaveSize 1
        val event = denied.single()
        event.recurso shouldBe recurso
        event.motivo shouldBe "RBAC"
        event.actorId shouldBe actor.userId
        event.clubId shouldBe actor.clubId
    }
}
