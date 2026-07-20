package com.runcriticon.identidad.application.usecases
import com.runcriticon.identidad.application.usecases.session.QueryMyPermissionsQuery
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.UUID

class QueryMyPermissionsTest :
    FunSpec({
        val principalProvider = mockk<PrincipalProvider>()
        val useCase = QueryMyPermissionsQuery(principalProvider)

        test("devuelve los permisos de la AuthorizationMatrix para el rol del principal actual") {
            val admin = Principal(userId = UUID.randomUUID(), clubId = UUID.randomUUID(), role = Role.ADMIN)
            every { principalProvider.current() } returns admin

            val result = useCase.execute()

            result[Resource.COACH] shouldBe setOf(Action.INVITE, Action.LIST)
            result[Resource.USER] shouldBe setOf(Action.REVOKE_SESSIONS, Action.DEACTIVATE)
        }

        test("un ALUMNO sin reglas en la matriz recibe un mapa vacío") {
            val alumno = Principal(userId = UUID.randomUUID(), clubId = UUID.randomUUID(), role = Role.ALUMNO)
            every { principalProvider.current() } returns alumno

            useCase.execute() shouldBe emptyMap()
        }
    })
