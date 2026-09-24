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
            result[Resource.USER] shouldBe setOf(Action.REVOKE_SESSIONS, Action.DEACTIVATE, Action.DELETE)
        }

        test("un ALUMNO puede ver su semana, reportar, gestionar consentimiento, marcas y reajustes") {
            val alumno = Principal(userId = UUID.randomUUID(), clubId = UUID.randomUUID(), role = Role.ALUMNO)
            every { principalProvider.current() } returns alumno

            useCase.execute() shouldBe
                mapOf(
                    Resource.RESOLVED_SESSION to setOf(Action.LIST),
                    Resource.SESSION_REPORT to setOf(Action.SUBMIT),
                    Resource.CONSENT to setOf(Action.GRANT, Action.REVOKE),
                    Resource.MARCA to setOf(Action.LIST, Action.RECORD, Action.WITHDRAW),
                    Resource.DAY_ADJUSTMENT to setOf(Action.RESCHEDULE, Action.WITHDRAW),
                )
        }

        test("un ENTRENADOR ve los permisos de la AuthorizationMatrix para su rol, no los de ADMIN ni ALUMNO") {
            val coach = Principal(userId = UUID.randomUUID(), clubId = UUID.randomUUID(), role = Role.ENTRENADOR)
            every { principalProvider.current() } returns coach

            val result = useCase.execute()

            result[Resource.STUDENT] shouldBe setOf(Action.INVITE, Action.CLASSIFY, Action.LIST)
            result[Resource.GROUP] shouldBe setOf(Action.CREATE, Action.LIST, Action.UPDATE)
            result[Resource.PLAN] shouldBe
                setOf(Action.CREATE, Action.LIST, Action.UPDATE, Action.PUBLISH, Action.PERSONALIZE)
            result.containsKey(Resource.USER) shouldBe false
            result.containsKey(Resource.CONSENT) shouldBe false
        }
    })
