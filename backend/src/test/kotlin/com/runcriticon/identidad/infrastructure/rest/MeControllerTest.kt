package com.runcriticon.identidad.infrastructure.rest

import com.runcriticon.identidad.application.usecases.QueryMyPermissions
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * Test unitario de [MeController]: verifica que delega en [QueryMyPermissions] sin contexto Spring.
 * La autenticación real y el enrutamiento de Spring MVC se cubren en integración con Testcontainers.
 */
class MeControllerTest :
    FunSpec({
        val queryMyPermissions = mockk<QueryMyPermissions>()
        val controller = MeController(queryMyPermissions)

        test("permissions devuelve el mapa del caso de uso tal cual") {
            val permissions = mapOf(Resource.COACH to setOf(Action.INVITE, Action.LIST))
            every { queryMyPermissions.execute() } returns permissions

            controller.permissions() shouldBe permissions
        }
    })
