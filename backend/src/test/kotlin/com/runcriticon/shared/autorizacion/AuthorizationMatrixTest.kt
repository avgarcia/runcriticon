package com.runcriticon.shared.autorizacion

import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AuthorizationMatrixTest :
    FunSpec({
        test("el ADMIN puede listar entrenadores, revocar sesiones y desactivar cuentas (LAL-13)") {
            AuthorizationMatrix.can(Role.ADMIN, Resource.COACH, Action.LIST) shouldBe true
            AuthorizationMatrix.can(Role.ADMIN, Resource.USER, Action.REVOKE_SESSIONS) shouldBe true
            AuthorizationMatrix.can(Role.ADMIN, Resource.USER, Action.DEACTIVATE) shouldBe true
        }

        test("ENTRENADOR y ALUMNO no pueden gestionar sesiones ni cuentas de otros usuarios") {
            listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
                AuthorizationMatrix.can(role, Resource.COACH, Action.LIST) shouldBe false
                AuthorizationMatrix.can(role, Resource.USER, Action.REVOKE_SESSIONS) shouldBe false
                AuthorizationMatrix.can(role, Resource.USER, Action.DEACTIVATE) shouldBe false
            }
        }

        test("default deny: una combinación rol/recurso/acción no declarada devuelve false") {
            AuthorizationMatrix.can(Role.ALUMNO, Resource.COACH, Action.INVITE) shouldBe false
        }
    })
