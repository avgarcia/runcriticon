package com.runcriticon.shared.autorizacion

import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AuthorizationMatrixTest :
    FunSpec({
        test("el ADMIN puede listar entrenadores, revocar sesiones y desactivar cuentas (LAL-13)") {
            AuthorizationMatrix.can(Role.ADMIN, Resource.COACH, Action.LIST) shouldBe true
            AuthorizationMatrix.can(Role.ADMIN, Resource.USER, Action.REVOKE_SESSIONS) shouldBe true
            AuthorizationMatrix.can(Role.ADMIN, Resource.USER, Action.DEACTIVATE) shouldBe true
            AuthorizationMatrix.can(Role.ADMIN, Resource.USER, Action.DELETE) shouldBe true
        }

        test("ENTRENADOR y ALUMNO no pueden gestionar sesiones ni cuentas de otros usuarios") {
            listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
                AuthorizationMatrix.can(role, Resource.COACH, Action.LIST) shouldBe false
                AuthorizationMatrix.can(role, Resource.USER, Action.REVOKE_SESSIONS) shouldBe false
                AuthorizationMatrix.can(role, Resource.USER, Action.DEACTIVATE) shouldBe false
                AuthorizationMatrix.can(role, Resource.USER, Action.DELETE) shouldBe false
            }
        }

        test("default deny: una combinación rol/recurso/acción no declarada devuelve false") {
            AuthorizationMatrix.can(Role.ALUMNO, Resource.COACH, Action.INVITE) shouldBe false
        }

        test("el ADMIN gestiona y lista la taxonomía; el ENTRENADOR solo la lista; el ALUMNO queda fuera") {
            AuthorizationMatrix.can(Role.ADMIN, Resource.TAXONOMY, Action.MANAGE) shouldBe true
            AuthorizationMatrix.can(Role.ADMIN, Resource.TAXONOMY, Action.LIST) shouldBe true

            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.TAXONOMY, Action.LIST) shouldBe true
            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.TAXONOMY, Action.MANAGE) shouldBe false

            AuthorizationMatrix.can(Role.ALUMNO, Resource.TAXONOMY, Action.LIST) shouldBe false
            AuthorizationMatrix.can(Role.ALUMNO, Resource.TAXONOMY, Action.MANAGE) shouldBe false
        }

        test("el ADMIN y el ENTRENADOR clasifican alumnos; el ALUMNO no") {
            AuthorizationMatrix.can(Role.ADMIN, Resource.STUDENT, Action.CLASSIFY) shouldBe true
            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.STUDENT, Action.CLASSIFY) shouldBe true
            AuthorizationMatrix.can(Role.ALUMNO, Resource.STUDENT, Action.CLASSIFY) shouldBe false
        }

        test("el ADMIN y el ENTRENADOR listan alumnos; el ALUMNO no") {
            AuthorizationMatrix.can(Role.ADMIN, Resource.STUDENT, Action.LIST) shouldBe true
            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.STUDENT, Action.LIST) shouldBe true
            AuthorizationMatrix.can(Role.ALUMNO, Resource.STUDENT, Action.LIST) shouldBe false
        }

        test("clasificar alumnos no le abre al ENTRENADOR la gestión del catálogo de ejes") {
            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.STUDENT, Action.CLASSIFY) shouldBe true

            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.TAXONOMY, Action.MANAGE) shouldBe false
            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.CLUB, Action.UPDATE) shouldBe false
        }

        test("grantedTo agrupa las acciones concedidas al ADMIN por recurso (ADR-0009 D18)") {
            val granted = AuthorizationMatrix.grantedTo(Role.ADMIN)

            granted[Resource.COACH] shouldBe setOf(Action.INVITE, Action.LIST)
            granted[Resource.STUDENT] shouldBe setOf(Action.INVITE, Action.CLASSIFY, Action.LIST)
            granted[Resource.USER] shouldBe setOf(Action.REVOKE_SESSIONS, Action.DEACTIVATE, Action.DELETE)
        }

        test("los grupos los crean, previsualizan y ajustan el admin y el entrenador, el alumno no") {
            listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
                AuthorizationMatrix.can(role, Resource.GROUP, Action.CREATE) shouldBe true
                AuthorizationMatrix.can(role, Resource.GROUP, Action.LIST) shouldBe true
                AuthorizationMatrix.can(role, Resource.GROUP, Action.UPDATE) shouldBe true
            }

            AuthorizationMatrix.can(Role.ALUMNO, Resource.GROUP, Action.CREATE) shouldBe false
            AuthorizationMatrix.can(Role.ALUMNO, Resource.GROUP, Action.LIST) shouldBe false
            AuthorizationMatrix.can(Role.ALUMNO, Resource.GROUP, Action.UPDATE) shouldBe false
        }

        test("el ALUMNO puede ver su semana, reportar, gestionar consentimiento, marcas y reajustes") {
            AuthorizationMatrix.grantedTo(Role.ALUMNO) shouldBe
                mapOf(
                    Resource.RESOLVED_SESSION to setOf(Action.LIST),
                    Resource.SESSION_REPORT to setOf(Action.SUBMIT),
                    Resource.CONSENT to setOf(Action.GRANT, Action.REVOKE),
                    Resource.MARCA to setOf(Action.LIST, Action.RECORD, Action.WITHDRAW),
                    Resource.DAY_ADJUSTMENT to setOf(Action.RESCHEDULE, Action.WITHDRAW),
                )
        }

        test("solo el ALUMNO ve la semana resuelta; ADMIN y ENTRENADOR quedan fuera") {
            AuthorizationMatrix.can(Role.ALUMNO, Resource.RESOLVED_SESSION, Action.LIST) shouldBe true

            AuthorizationMatrix.can(Role.ADMIN, Resource.RESOLVED_SESSION, Action.LIST) shouldBe false
            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.RESOLVED_SESSION, Action.LIST) shouldBe false
        }

        test("solo el ALUMNO reporta sesiones; ADMIN y ENTRENADOR quedan fuera") {
            AuthorizationMatrix.can(Role.ALUMNO, Resource.SESSION_REPORT, Action.SUBMIT) shouldBe true

            AuthorizationMatrix.can(Role.ADMIN, Resource.SESSION_REPORT, Action.SUBMIT) shouldBe false
            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.SESSION_REPORT, Action.SUBMIT) shouldBe false
        }

        test("solo el ALUMNO concede o revoca su propio consentimiento; ADMIN y ENTRENADOR quedan fuera (LAL-128)") {
            AuthorizationMatrix.can(Role.ALUMNO, Resource.CONSENT, Action.GRANT) shouldBe true
            AuthorizationMatrix.can(Role.ALUMNO, Resource.CONSENT, Action.REVOKE) shouldBe true

            AuthorizationMatrix.can(Role.ADMIN, Resource.CONSENT, Action.GRANT) shouldBe false
            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.CONSENT, Action.GRANT) shouldBe false
            AuthorizationMatrix.can(Role.ADMIN, Resource.CONSENT, Action.REVOKE) shouldBe false
            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.CONSENT, Action.REVOKE) shouldBe false
        }

        test("solo el ALUMNO gestiona sus marcas; ADMIN y ENTRENADOR quedan fuera, ni siquiera para listar (LAL-31)") {
            AuthorizationMatrix.can(Role.ALUMNO, Resource.MARCA, Action.LIST) shouldBe true
            AuthorizationMatrix.can(Role.ALUMNO, Resource.MARCA, Action.RECORD) shouldBe true
            AuthorizationMatrix.can(Role.ALUMNO, Resource.MARCA, Action.WITHDRAW) shouldBe true

            listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
                AuthorizationMatrix.can(role, Resource.MARCA, Action.LIST) shouldBe false
                AuthorizationMatrix.can(role, Resource.MARCA, Action.RECORD) shouldBe false
                AuthorizationMatrix.can(role, Resource.MARCA, Action.WITHDRAW) shouldBe false
            }
        }

        test(
            "solo el ALUMNO reajusta o deshace el reajuste de sus sesiones; ADMIN y ENTRENADOR quedan fuera (LAL-33)",
        ) {
            AuthorizationMatrix.can(Role.ALUMNO, Resource.DAY_ADJUSTMENT, Action.RESCHEDULE) shouldBe true
            AuthorizationMatrix.can(Role.ALUMNO, Resource.DAY_ADJUSTMENT, Action.WITHDRAW) shouldBe true

            listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
                AuthorizationMatrix.can(role, Resource.DAY_ADJUSTMENT, Action.RESCHEDULE) shouldBe false
                AuthorizationMatrix.can(role, Resource.DAY_ADJUSTMENT, Action.WITHDRAW) shouldBe false
            }
        }

        test("solo el ENTRENADOR ve el panel de alertas; ADMIN y ALUMNO quedan fuera (LAL-116)") {
            AuthorizationMatrix.can(Role.ENTRENADOR, Resource.COACH_ALERT, Action.LIST) shouldBe true

            listOf(Role.ADMIN, Role.ALUMNO).forEach { role ->
                AuthorizationMatrix.can(role, Resource.COACH_ALERT, Action.LIST) shouldBe false
            }
        }

        test("solo el ADMIN ve la vista de salud del club; ENTRENADOR y ALUMNO quedan fuera") {
            AuthorizationMatrix.can(Role.ADMIN, Resource.CLUB_HEALTH, Action.LIST) shouldBe true

            listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
                AuthorizationMatrix.can(role, Resource.CLUB_HEALTH, Action.LIST) shouldBe false
            }
        }

        test("solo el ENTRENADOR crea, lista, edita, publica y personaliza planes; ADMIN y ALUMNO quedan fuera") {
            listOf(Action.CREATE, Action.LIST, Action.UPDATE, Action.PUBLISH, Action.PERSONALIZE).forEach { action ->
                AuthorizationMatrix.can(Role.ENTRENADOR, Resource.PLAN, action) shouldBe true
                AuthorizationMatrix.can(Role.ADMIN, Resource.PLAN, action) shouldBe false
                AuthorizationMatrix.can(Role.ALUMNO, Resource.PLAN, action) shouldBe false
            }
        }

        test("solo el ADMIN consulta el log de auditoría; ENTRENADOR y ALUMNO quedan fuera") {
            AuthorizationMatrix.can(Role.ADMIN, Resource.AUDIT_EVENT, Action.LIST) shouldBe true

            listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
                AuthorizationMatrix.can(role, Resource.AUDIT_EVENT, Action.LIST) shouldBe false
            }
        }

        test("el ADMIN y el ENTRENADOR ven y descartan sugerencias de fusión de grupos; el ALUMNO no") {
            listOf(Action.LIST, Action.DISMISS).forEach { action ->
                AuthorizationMatrix.can(Role.ADMIN, Resource.GROUP_MERGE_SUGGESTION, action) shouldBe true
                AuthorizationMatrix.can(Role.ENTRENADOR, Resource.GROUP_MERGE_SUGGESTION, action) shouldBe true
                AuthorizationMatrix.can(Role.ALUMNO, Resource.GROUP_MERGE_SUGGESTION, action) shouldBe false
            }
        }

        test("solo el ADMIN asigna entrenadores a un grupo; el ENTRENADOR no puede autoasignarse") {
            AuthorizationMatrix.can(Role.ADMIN, Resource.GROUP, Action.ASSIGN_COACH) shouldBe true

            listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
                AuthorizationMatrix.can(role, Resource.GROUP, Action.ASSIGN_COACH) shouldBe false
            }
        }

        test("grantedTo agrupa las acciones concedidas al ENTRENADOR por recurso") {
            val granted = AuthorizationMatrix.grantedTo(Role.ENTRENADOR)

            granted[Resource.STUDENT] shouldBe setOf(Action.INVITE, Action.CLASSIFY, Action.LIST)
            granted[Resource.TAXONOMY] shouldBe setOf(Action.LIST)
            granted[Resource.GROUP] shouldBe setOf(Action.CREATE, Action.LIST, Action.UPDATE)
            granted[Resource.GROUP_MERGE_SUGGESTION] shouldBe setOf(Action.LIST, Action.DISMISS)
            granted[Resource.PLAN] shouldBe
                setOf(Action.CREATE, Action.LIST, Action.UPDATE, Action.PUBLISH, Action.PERSONALIZE)
            granted[Resource.COACH_ALERT] shouldBe setOf(Action.LIST)
            granted.containsKey(Resource.CLUB_HEALTH) shouldBe false
            granted.containsKey(Resource.AUDIT_EVENT) shouldBe false
        }

        test("can() y grantedTo() son consistentes para cada rol, recurso y acción") {
            Role.entries.forEach { role ->
                val granted = AuthorizationMatrix.grantedTo(role)
                Resource.entries.forEach { resource ->
                    Action.entries.forEach { action ->
                        val expected = granted[resource]?.contains(action) ?: false
                        withClue("$role/$resource/$action") {
                            AuthorizationMatrix.can(role, resource, action) shouldBe expected
                        }
                    }
                }
            }
        }
    })
